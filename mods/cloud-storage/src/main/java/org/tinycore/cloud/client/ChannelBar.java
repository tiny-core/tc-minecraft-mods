package org.tinycore.cloud.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.tinycore.cloud.cloud.ChannelNames;
import org.tinycore.cloud.menu.LinkAction;
import org.tinycore.cloud.menu.LinkChannel;
import org.tinycore.cloud.menu.LinkHeader;
import org.tinycore.core.client.ui.IconButton;
import org.tinycore.core.client.ui.ScreenStyle;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Linha de canais no topo da tela do TC Cloud Link (só cliente): {@code ◀ nome ▶} para trocar o canal deste Link,
 * {@code +} para criar e {@code ✎} para renomear. Criar/renomear abrem uma caixa de texto no lugar do nome: Enter
 * confirma, Esc cancela.
 * <p>
 * Nada é decidido aqui: cada ação vira um {@code LinkActionPayload} (o servidor confere dono, nome e limite) e o
 * nome mostrado é sempre o que o servidor mandou no cabeçalho. Separada do {@code CloudLinkScreen} para ele não
 * passar do tamanho combinado.
 */
final class ChannelBar {

    private static final int BUTTON = 11;
    private static final int GAP = 2;

    private enum Mode { VIEW, CREATE, RENAME }

    private final Font font;
    /** Manda a ação ao servidor (ação, texto). */
    private final BiConsumer<LinkAction, String> send;
    /** Dá o foco do teclado da tela à caixa (sem isso as letras não chegam a ela). */
    private Consumer<GuiEventListener> focus = listener -> {};
    private Mode mode = Mode.VIEW;
    private int x;
    private int y;
    private int width;
    private IconButton previous;
    private IconButton next;
    private IconButton create;
    private IconButton rename;
    private EditBox editor;
    private LinkHeader header = LinkHeader.EMPTY;

    ChannelBar(Font font, BiConsumer<LinkAction, String> send) {
        this.font = font;
        this.send = send;
    }

    /** Posiciona e cria os widgets (no {@code init()} da tela, que roda de novo ao redimensionar). */
    void init(UnaryOperator<IconButton> addButton, UnaryOperator<EditBox> addBox, Consumer<GuiEventListener> focus,
              int x, int y, int width) {
        this.focus = focus;
        this.x = x;
        this.y = y;
        this.width = width;
        int right = x + width;
        rename = addButton.apply(new IconButton(right - BUTTON, y, BUTTON, () -> start(Mode.RENAME)).glyph("✎"));
        rename.setTooltipText(Component.translatable("gui.tccloud.channel.rename"));
        create = addButton.apply(new IconButton(right - BUTTON * 2 - GAP, y, BUTTON, () -> start(Mode.CREATE)).glyph("+"));
        create.setTooltipText(Component.translatable("gui.tccloud.channel.create"));
        next = addButton.apply(new IconButton(right - BUTTON * 3 - GAP * 3, y, BUTTON, () -> step(1)).glyph("▶"));
        next.setTooltipText(Component.translatable("gui.tccloud.channel.next"));
        previous = addButton.apply(new IconButton(x + labelWidth(), y, BUTTON, () -> step(-1)).glyph("◀"));
        previous.setTooltipText(Component.translatable("gui.tccloud.channel.previous"));
        String typed = editor == null ? "" : editor.getValue();
        editor = addBox.apply(new EditBox(font, nameX() + 3, y + 2, nameWidth() - 6, 9,
                Component.translatable("gui.tccloud.channel.name")));
        editor.setBordered(false);
        editor.setMaxLength(ChannelNames.MAX_LENGTH);
        editor.setTextColor(ScreenStyle.TEXT);
        editor.setValue(typed);
        editor.visible = mode != Mode.VIEW;
    }

    private int labelWidth() {
        return font.width(Component.translatable("gui.tccloud.channel")) + 4;
    }

    private int nameX() {
        return x + labelWidth() + BUTTON + GAP;
    }

    private int nameWidth() {
        return next.getX() - GAP - nameX();
    }

    /** Cabeçalho novo do servidor: atualiza botões (sem canais = nuvem desligada, tudo desativado). */
    void update(LinkHeader header) {
        this.header = header;
        boolean any = !header.channels().isEmpty();
        previous.active = header.channels().size() > 1 && mode == Mode.VIEW;
        next.active = previous.active;
        create.active = any && mode == Mode.VIEW;
        rename.active = header.selected() >= 0 && mode == Mode.VIEW;
    }

    void render(GuiGraphics g) {
        g.drawString(font, Component.translatable("gui.tccloud.channel"), x, y + 2, ScreenStyle.TEXT_MUTED, false);
        ScreenStyle.inset(g, nameX(), y, nameWidth(), BUTTON, ScreenStyle.SLOT);
        if (mode != Mode.VIEW) {
            return; // a caixa de texto desenha o que está sendo digitado
        }
        LinkChannel channel = selected();
        Component name = channel == null ? Component.literal("—") : Component.literal(channel.name());
        String position = channel == null ? "" : (header.selected() + 1) + "/" + header.channels().size();
        int positionWidth = position.isEmpty() ? 0 : font.width(position) + 4;
        ScreenStyle.drawFitted(g, font, name, nameX() + 3, y + 2, nameWidth() - 6 - positionWidth, ScreenStyle.TEXT);
        if (!position.isEmpty()) {
            g.drawString(font, position, nameX() + nameWidth() - 3 - font.width(position), y + 2,
                    ScreenStyle.TEXT_MUTED, false);
        }
    }

    private @Nullable LinkChannel selected() {
        List<LinkChannel> channels = header.channels();
        int index = header.selected();
        return index >= 0 && index < channels.size() ? channels.get(index) : null;
    }

    private void step(int direction) {
        List<LinkChannel> channels = header.channels();
        if (channels.size() < 2) return;
        int index = Math.floorMod(Math.max(0, header.selected()) + direction, channels.size());
        send.accept(LinkAction.SELECT_CHANNEL, channels.get(index).id().toString());
    }

    private void start(Mode wanted) {
        mode = wanted;
        LinkChannel channel = selected();
        editor.setValue(wanted == Mode.RENAME && channel != null ? channel.name() : "");
        editor.visible = true;
        editor.setFocused(true);
        focus.accept(editor);
        update(header);
    }

    private void finish(boolean confirm) {
        if (confirm && !editor.getValue().isBlank()) {
            send.accept(mode == Mode.CREATE ? LinkAction.CREATE_CHANNEL : LinkAction.RENAME_CHANNEL, editor.getValue());
        }
        mode = Mode.VIEW;
        editor.setFocused(false);
        editor.visible = false;
        focus.accept(null);
        update(header);
    }

    /** true se a caixa de texto está aberta (a tela entrega as teclas a ela). */
    boolean isEditing() {
        return mode != Mode.VIEW;
    }

    /** Teclado com a caixa aberta: Enter confirma, Esc cancela, o resto vai para a caixa. */
    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isEditing()) return false;
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            finish(true);
        } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            finish(false);
        } else {
            editor.keyPressed(keyCode, scanCode, modifiers);
        }
        return true;
    }

    /** Clique fora da caixa aberta cancela a edição (como sair de uma caixa de texto). */
    void mouseClicked(double mouseX, double mouseY) {
        if (isEditing() && !editor.isMouseOver(mouseX, mouseY)) finish(false);
    }
}
