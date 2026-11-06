package mobiletunnel

import (
	"context"
	"encoding/json"
	"fmt"
	"net"
	"sync"
	"time"

	"github.com/modelcontextprotocol/go-sdk/mcp"
	tunnelclient "github.com/openai/tunnel-client"
)

var runtimeState = struct {
	sync.Mutex
	status string
	cancel context.CancelFunc
}{status: "stopped"}

// Start launches the official OpenAI Tunnel Go SDK inside the Android process.
func Start(tunnelID, apiKey string, bridgePort int32, bridgeToken string) error {
	if tunnelID == "" || apiKey == "" || bridgeToken == "" || bridgePort < 1 || bridgePort > 65535 {
		return fmt.Errorf("Tunnel ID, runtime API key, and local bridge settings are required")
	}
	runtimeState.Lock()
	if runtimeState.cancel != nil {
		runtimeState.Unlock()
		return fmt.Errorf("tunnel is already running")
	}
	ctx, cancel := context.WithCancel(context.Background())
	runtimeState.cancel = cancel
	runtimeState.status = "starting"
	runtimeState.Unlock()

	go run(ctx, tunnelID, apiKey, int(bridgePort), bridgeToken)
	return nil
}

// Stop cancels the embedded Tunnel runtime.
func Stop() {
	runtimeState.Lock()
	cancel := runtimeState.cancel
	runtimeState.Unlock()
	if cancel != nil {
		cancel()
	}
}

// Status returns a short runtime state without exposing credentials.
func Status() string {
	runtimeState.Lock()
	defer runtimeState.Unlock()
	return runtimeState.status
}

func setState(ctx context.Context, state string) {
	runtimeState.Lock()
	defer runtimeState.Unlock()
	if runtimeState.cancel != nil && ctx.Err() == nil {
		runtimeState.status = state
	}
}

func run(ctx context.Context, tunnelID, apiKey string, bridgePort int, bridgeToken string) {
	defer func() {
		runtimeState.Lock()
		runtimeState.cancel = nil
		if ctx.Err() != nil {
			runtimeState.status = "stopped"
		} else if runtimeState.status == "starting" || runtimeState.status == "connecting" {
			runtimeState.status = "stopped"
		}
		runtimeState.Unlock()
	}()

	server := mcp.NewServer(&mcp.Implementation{Name: "android-mcp-remote-control", Version: "0.1.0-poc.1"}, nil)
	mcp.AddTool(server, &mcp.Tool{
		Name:        "device_info",
		Description: "Read the model and Android version of the device using its local Wireless ADB connection.",
	}, func(callCtx context.Context, _ *mcp.CallToolRequest, _ map[string]any) (*mcp.CallToolResult, map[string]any, error) {
		value, err := callBridge(callCtx, bridgePort, bridgeToken, "device_info", nil)
		if err != nil {
			return nil, nil, err
		}
		return textResult(value), value, nil
	})
	mcp.AddTool(server, &mcp.Tool{
		Name:        "tap_test_zone",
		Description: "Tap the test area in this app using the device's local Wireless ADB connection. This tool is disabled until the user enables Remote tap in the app.",
	}, func(callCtx context.Context, _ *mcp.CallToolRequest, _ map[string]any) (*mcp.CallToolResult, map[string]any, error) {
		value, err := callBridge(callCtx, bridgePort, bridgeToken, "tap_test_zone", nil)
		if err != nil {
			return nil, nil, err
		}
		return textResult(value), value, nil
	})

	serverTransport, tunnelTransport := mcp.NewInMemoryTransports()
	serverCtx, stopServer := context.WithCancel(ctx)
	defer stopServer()
	serverDone := make(chan error, 1)
	go func() { serverDone <- server.Run(serverCtx, serverTransport) }()

	client, err := tunnelclient.New(tunnelclient.Config{TunnelID: tunnelID, APIKey: apiKey}, tunnelTransport)
	if err != nil {
		setState(ctx, "error: "+err.Error())
		return
	}
	if err := client.Start(ctx); err != nil {
		setState(ctx, "error: "+err.Error())
		return
	}
	setState(ctx, "connecting")
	if err := client.WaitUntilReady(ctx); err != nil {
		if ctx.Err() == nil {
			setState(ctx, "error: "+err.Error())
		}
		return
	}
	setState(ctx, "ready")

	select {
	case <-ctx.Done():
		stopCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		_ = client.Stop(stopCtx)
	case <-client.Done():
		setState(ctx, "error: Tunnel runtime stopped")
	case err := <-serverDone:
		if err != nil && ctx.Err() == nil {
			setState(ctx, "error: MCP server stopped: "+err.Error())
		}
	}
}

func callBridge(ctx context.Context, port int, token, method string, args map[string]any) (map[string]any, error) {
	dialer := net.Dialer{Timeout: 8 * time.Second}
	conn, err := dialer.DialContext(ctx, "tcp", fmt.Sprintf("127.0.0.1:%d", port))
	if err != nil {
		return nil, fmt.Errorf("local ADB bridge unavailable: %w", err)
	}
	defer conn.Close()
	_ = conn.SetDeadline(time.Now().Add(10 * time.Second))
	request, err := json.Marshal(map[string]any{"token": token, "method": method, "args": args})
	if err != nil {
		return nil, err
	}
	if _, err := fmt.Fprintf(conn, "%s\n", request); err != nil {
		return nil, err
	}
	var response struct {
		OK     bool           `json:"ok"`
		Result map[string]any `json:"result"`
		Error  string         `json:"error"`
	}
	if err := json.NewDecoder(conn).Decode(&response); err != nil {
		return nil, err
	}
	if !response.OK {
		return nil, fmt.Errorf("%s", response.Error)
	}
	return response.Result, nil
}

func textResult(value map[string]any) *mcp.CallToolResult {
	encoded, _ := json.Marshal(value)
	return &mcp.CallToolResult{Content: []mcp.Content{&mcp.TextContent{Text: string(encoded)}}}
}
