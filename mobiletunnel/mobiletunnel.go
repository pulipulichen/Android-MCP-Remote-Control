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

	server := mcp.NewServer(&mcp.Implementation{Name: "android-mcp-remote-control", Version: "0.1.0-poc.2"}, nil)
	bridge := adbBridge{port: bridgePort, token: bridgeToken}
	addBridgeTool[struct{}](server, bridge, "device_info", "Read the model and Android version via Wireless ADB.")
	addBridgeTool[struct{}](server, bridge, "tap_test_zone", "Tap the app's test area. Requires the Remote tap switch.")
	addBridgeTool[struct {
		ThirdPartyOnly bool `json:"third_party_only,omitempty" jsonschema:"List only third-party packages"`
	}](server, bridge, "list_packages", "List installed Android packages.")
	addBridgeTool[packageArgs](server, bridge, "package_info", "Read package details including version, activities, and permissions.")
	addBridgeTool[packageArgs](server, bridge, "launch_app", "Launch the package's launcher activity.")
	addBridgeTool[struct {
		Lines int `json:"lines,omitempty" jsonschema:"Recent Logcat lines, from 1 to 1000 (default 100)"`
	}](server, bridge, "logcat", "Read a snapshot of recent Android Logcat entries.")
	addBridgeTool[struct {
		Command string `json:"command" jsonschema:"Single Android ADB shell command to run"`
	}](server, bridge, "adb_shell", "Run a command with Android ADB shell permissions and return stdout, stderr, and exit code.")
	addBridgeTool[struct {
		URL    string `json:"url" jsonschema:"HTTPS download URL for a standalone APK"`
		SHA256 string `json:"sha256,omitempty" jsonschema:"Expected SHA-256 in hexadecimal"`
	}](server, bridge, "install_apk", "Download and install a standalone APK or update a signature-compatible app, preserving its data.")
	addBridgeTool[struct {
		PackageName string `json:"package_name" jsonschema:"Android package name"`
		KeepData    bool   `json:"keep_data,omitempty" jsonschema:"Preserve app data and cache on uninstall"`
	}](server, bridge, "uninstall_app", "Uninstall an Android app by package name. App data is removed by default.")

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

type packageArgs struct {
	PackageName string `json:"package_name" jsonschema:"Android package name"`
}

type adbBridge struct {
	port  int
	token string
}

func addBridgeTool[T any](server *mcp.Server, bridge adbBridge, name, description string) {
	if name != "device_info" && name != "tap_test_zone" {
		description += " Requires the Remote developer tools switch on the device."
	}
	mcp.AddTool(server, &mcp.Tool{Name: name, Description: description},
		func(ctx context.Context, _ *mcp.CallToolRequest, args T) (*mcp.CallToolResult, map[string]any, error) {
			value, err := bridge.call(ctx, name, args)
			if err != nil {
				return nil, nil, err
			}
			return textResult(value), value, nil
		})
}

func (bridge adbBridge) call(ctx context.Context, method string, args any) (map[string]any, error) {
	dialer := net.Dialer{Timeout: 8 * time.Second}
	conn, err := dialer.DialContext(ctx, "tcp", fmt.Sprintf("127.0.0.1:%d", bridge.port))
	if err != nil {
		return nil, fmt.Errorf("local ADB bridge unavailable: %w", err)
	}
	defer conn.Close()
	timeout := 40 * time.Second
	if method == "install_apk" {
		timeout = 190 * time.Second
	}
	_ = conn.SetDeadline(time.Now().Add(timeout))
	stop := context.AfterFunc(ctx, func() { _ = conn.Close() })
	defer stop()
	request, err := json.Marshal(map[string]any{"token": bridge.token, "method": method, "args": args})
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
