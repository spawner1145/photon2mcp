package dev.photonmcp.ui;

import com.lowdragmc.lowdraglib2.editor.ui.menu.MenuTab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import dev.vfyjxf.taffy.style.FlexDirection;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.lowdragmc.photon.gui.editor.FXEditor;
import dev.photonmcp.PhotonMcp;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class McpMenu extends MenuTab {
    public McpMenu(FXEditor editor) {
        super(editor);
    }

    @Override
    protected Component getComponent() {
        return Component.literal("MCP");
    }

    @Override
    protected TreeBuilder.Menu createDefaultMenu() {
        var mod = PhotonMcp.get();
        return TreeBuilder.Menu.start()
                .leaf(Component.translatable("photon_mcp.menu.status"), this::showStatus)
                .leaf(Component.translatable("photon_mcp.menu.configure"), this::showConfig)
                .leaf(Component.translatable(mod.server.isRunning() ? "photon_mcp.menu.stop" : "photon_mcp.menu.start"), () -> {
                    try {
                        if (mod.server.isRunning()) mod.server.close();
                        else mod.start();
                    } catch (Exception exception) {
                        showError(exception);
                    }
                })
                .leaf(Component.translatable("photon_mcp.menu.copy_url"), () -> Minecraft.getInstance().keyboardHandler.setClipboard(mod.config().endpoint()));
    }

    private void showStatus() {
        var dialog = createDialog();
        dialog.setTitle("photon_mcp.menu.status");
        var status = new TextElement();
        status.layout(layout -> layout.widthPercent(100).flexShrink(0));
        status.textStyle(style -> style.adaptiveHeight(true).textWrap(TextWrap.WRAP).textAlignHorizontal(Horizontal.LEFT));
        Runnable update = () -> {
            var mod = PhotonMcp.get();
            var data = mod.server.status();
            status.setText(Component.literal(
                    "Server: " + (mod.server.isRunning() ? "ON" : "OFF") + "\n"
                    + "URL: " + mod.config().endpoint() + "\n"
                    + "Clients (last 5 min): " + data.get("recentClients") + "\n"
                    + "Requests: " + data.get("requests") + "\n"
                    + "Last tool: " + data.get("lastTool").getAsString() + "\n"
                    + "Last request: " + data.get("lastRequest").getAsString() + "\n"
                    + "Error: " + mod.startupError() + " " + data.get("lastError").getAsString()));
        };
        update.run();
        status.addEventListener(UIEvents.TICK, event -> update.run());
        dialog.addContent(new ScrollerView().addScrollViewChild(status)
                .layout(layout -> layout.widthPercent(100).height(150)));
        dialog.addButton(new Button().setText("ldlib.gui.tips.confirm").setOnClick(event -> dialog.close()));
        dialog.show(editor);
    }

    private void showConfig() {
        var mod = PhotonMcp.get();
        var dialog = createDialog();
        dialog.setTitle("photon_mcp.menu.configure");
        var host = new TextField().setText(mod.config().host());
        var port = new TextField().setText(Integer.toString(mod.config().port()));
        dialog.addContent(configRow("Host", host));
        dialog.addContent(configRow("Port", port));
        dialog.addButton(new Button().setText("photon_mcp.menu.apply").setOnClick(event -> {
            try {
                mod.configure(host.getText(), Integer.parseInt(port.getText()));
                dialog.close();
            } catch (Exception exception) {
                showError(exception);
            }
        }));
        dialog.addButton(new Button().setText("ldlib.gui.tips.cancel").setOnClick(event -> dialog.close()));
        dialog.show(editor);
    }

    private void showError(Exception exception) {
        var dialog = createDialog().setTitle("MCP");
        var message = new TextElement().setText(Component.literal(exception.toString()));
        message.layout(layout -> layout.widthPercent(100));
        message.textStyle(style -> style.adaptiveHeight(true).textWrap(TextWrap.WRAP));
        dialog.addContent(new ScrollerView().addScrollViewChild(message)
                .layout(layout -> layout.widthPercent(100).height(100)));
        dialog.addButton(new Button().setText("ldlib.gui.tips.confirm").setOnClick(event -> dialog.close()));
        dialog.show(editor);
    }

    private Dialog createDialog() {
        var dialog = new Dialog();
        dialog.overlay.layout(layout -> layout.width(300).maxWidthPercent(95).maxHeightPercent(95));
        dialog.contentContainer.layout(layout -> layout.minHeight(0));
        return dialog;
    }

    private UIElement configRow(String name, TextField input) {
        var label = new Label().setText(Component.literal(name));
        label.layout(layout -> layout.width(36).flexShrink(0));
        input.layout(layout -> layout.flex(1).minWidth(0).height(16));
        return new UIElement().layout(layout -> layout.widthPercent(100)
                .flexDirection(FlexDirection.ROW).gapAll(4)).addChildren(label, input);
    }
}
