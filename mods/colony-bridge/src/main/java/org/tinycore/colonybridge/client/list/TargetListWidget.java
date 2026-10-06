package org.tinycore.colonybridge.client.list;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.tinycore.colonybridge.Config;
import org.tinycore.colonybridge.logic.target.TargetList;
import org.tinycore.colonybridge.logic.target.TargetListKind;
import org.tinycore.colonybridge.menu.TargetLineView;
import org.tinycore.colonybridge.menu.TargetListSync;
import org.tinycore.colonybridge.network.TargetEditPayload.Op;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static org.tinycore.colonybridge.client.list.TargetRowLayout.AMOUNT_WIDTH;
import static org.tinycore.colonybridge.client.list.TargetRowLayout.BUTTON;
import static org.tinycore.colonybridge.client.list.TargetRowLayout.in;

/**
 * Lista de linhas editável (só cliente), usada pelo Abastecedor (Manter / Excedente) e pelo filtro da Ponte.
 * Cada linha: ícone (slot virtual), caixa de texto com o alvo ({@code id}, {@code #tag}, {@code @mod}),
 * quantidade, "∞" (tudo, só no Excedente) e "x" (remover). O botão "+" fica em cima da lista.
 * <p>
 * <b>Nada é decidido aqui:</b> cada ação vira um {@code TargetEditPayload} ({@link TargetEditSender}) e a lista
 * mostrada é sempre a que o servidor mandou ({@link TargetListSync}). O texto só é enviado ao apertar Enter ou
 * sair da caixa; texto inválido fica vermelho e não é enviado. O ícone não é um {@code Slot}: clicar com um
 * item manda "use o item do meu cursor" e o servidor lê o cursor ele mesmo.
 * <p>
 * A linha "rascunho" (aberta pelo "+") existe só na tela até ganhar um alvo — assim o servidor nunca guarda
 * linha vazia. As caixas de texto ({@link EditBox}, widget de texto do Minecraft) são criadas uma por linha
 * visível e reaproveitadas ao rolar.
 * <p>
 * Esta classe guarda o estado (rolagem, rascunho) e trata a entrada; o desenho e as dicas ficam no
 * {@link TargetListPainter}, e as caixas de texto no {@link TargetRowEditor}.
 */
public final class TargetListWidget {

    /** Dados extras por linha, fornecidos pela tela (Abastecedor): cor da faixa e linhas da dica. */
    public interface LineInfo {
        int color(int index);

        void appendTooltip(int index, List<Component> lines);
    }

    /** Área onde o JEI pode soltar um item; {@code index = -1} = linha nova. */
    public record DropTarget(Rect2i area, int index) {}

    private final Font font;
    private final TargetListKind kind;
    private final TargetListSync sync;
    private final TargetEditSender sender;
    private final @Nullable LineInfo info;
    private final int rows;
    private final TargetRowEditor editor;
    private final TargetListPainter painter;

    private TargetRowLayout layout;
    private boolean visible = true;
    private int firstRow;
    /** Linha rascunho aberta pelo "+" (só no cliente). */
    private boolean draft;

    public TargetListWidget(Font font, TargetListKind kind, int containerId, TargetListSync sync,
                            @Nullable LineInfo info, int rows) {
        this.font = font;
        this.kind = kind;
        this.sync = sync;
        this.sender = new TargetEditSender(containerId, kind);
        this.info = info;
        this.rows = rows;
        this.editor = new TargetRowEditor(kind, sender, rows);
        this.painter = new TargetListPainter(font, kind, info, rows);
    }

    /**
     * Posiciona a lista e cria as caixas de texto (chamar no {@code init()} da tela, que roda de novo ao
     * redimensionar a janela).
     *
     * @param add   registra a caixa na tela ({@code addRenderableWidget})
     * @param focus dá o foco do teclado da tela a uma caixa ({@code setFocused})
     * @param y     topo da primeira linha (o "+" fica logo acima)
     */
    public void init(UnaryOperator<EditBox> add, Consumer<GuiEventListener> focus, int x, int y, int width) {
        layout = TargetRowLayout.of(kind, x, y, width, rows);
        editor.init(font, layout, add, focus);
        setVisible(visible);
    }

    public void setVisible(boolean value) {
        visible = value;
        editor.setVisible(value);
    }

    public boolean isVisible() {
        return visible;
    }

    private List<TargetLineView> lines() {
        return sync.lines(kind);
    }

    private int totalRows() {
        return lines().size() + (draft ? 1 : 0);
    }

    private boolean isDraft(int index) {
        return draft && index == lines().size();
    }

    // ---------------------------------------------------------------- tick

    /**
     * A cada tick do cliente: confirma caixas que perderam o foco, liga as caixas às linhas e pinta o texto.
     * Roda também com a lista escondida, para não perder o que foi digitado antes de trocar de aba.
     */
    public void tick() {
        firstRow = Math.max(0, Math.min(firstRow, totalRows() - rows));
        if (editor.tick(lines(), firstRow, draft, sync.revision())) {
            draft = false; // o rascunho virou linha de verdade (pacote enviado)
        }
    }

    // ---------------------------------------------------------------- desenho

    /** Fundo, ícones e botões (as caixas de texto são desenhadas pela tela, por cima). */
    public void render(GuiGraphics g, int mouseX, int mouseY) {
        if (visible) {
            painter.render(g, layout, lines(), firstRow, draft, mouseX, mouseY);
        }
    }

    // ---------------------------------------------------------------- entrada

    /**
     * Clique na lista.
     *
     * @param carried item no cursor do jogador (só para saber se é "trocar pelo item"; quem lê o item é o servidor)
     * @return true se o clique foi da lista
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button, ItemStack carried) {
        if (!visible) {
            return false;
        }
        if (editor.isOverBox(mouseX, mouseY)) {
            return false; // a tela entrega o clique à caixa (foco, cursor do texto)
        }
        editor.unfocusAll();
        if (in(mouseX, mouseY, layout.addX(), layout.addY(), BUTTON, BUTTON)) {
            startDraft();
            return true;
        }
        int row = layout.rowAt(mouseX, mouseY);
        if (row < 0) {
            return layout.isOver(mouseX, mouseY);
        }
        int index = firstRow + row;
        int y = layout.rowY(row);
        List<TargetLineView> lines = lines();
        if (in(mouseX, mouseY, layout.removeX(), y + 4, BUTTON, BUTTON) && index < totalRows()) {
            if (isDraft(index)) {
                draft = false;
            } else {
                sender.send(Op.REMOVE, index);
                editor.forgetOrigins();
            }
            return true;
        }
        if (index < lines.size() && kind.allowsAll() && in(mouseX, mouseY, layout.allX(), y + 4, BUTTON, BUTTON)) {
            TargetLineView line = lines.get(index);
            sender.sendAmount(index, line.amount(), !line.all());
            return true;
        }
        if (in(mouseX, mouseY, layout.iconX(), y + 1, 18, 18)) {
            clickIcon(index, button, carried);
        }
        return true;
    }

    private void clickIcon(int index, int button, ItemStack carried) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && index < lines().size()) {
            editor.cycleTag(index, lines().get(index));
            return;
        }
        if (carried.isEmpty()) {
            return;
        }
        if (isDraft(index)) {
            sender.send(Op.ADD_CURSOR, -1);
            draft = false;
        } else if (index < lines().size()) {
            sender.send(Op.SET_CURSOR, index);
        }
    }

    /** Roda: sobre a quantidade muda o valor (Shift ×10, Ctrl ×64); no resto rola a lista. */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY, boolean shift, boolean control) {
        if (!visible || !layout.isOver(mouseX, mouseY)) {
            return false;
        }
        int row = layout.rowAt(mouseX, mouseY);
        int index = firstRow + row;
        if (row >= 0 && kind.hasAmount() && index < lines().size()
                && in(mouseX, mouseY, layout.amountX(), layout.rowY(row) + 3, AMOUNT_WIDTH, 14)) {
            TargetLineView line = lines().get(index);
            if (!line.all()) {
                int step = control ? 64 : shift ? 10 : 1;
                int amount = Math.max(0, Math.min(TargetListKind.MAX_AMOUNT, line.amount() + (int) Math.signum(scrollY) * step));
                sender.sendAmount(index, amount, false);
            }
            return true;
        }
        editor.unfocusAll();
        firstRow = Math.max(0, Math.min(firstRow - (int) Math.signum(scrollY), totalRows() - rows));
        return true;
    }

    /** Teclado: Enter confirma, Esc sai da caixa; com uma caixa em foco, nenhuma tecla fecha a tela. */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return visible && editor.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean isEditing() {
        return editor.isEditing();
    }

    private void startDraft() {
        if (lines().size() >= maxLines()) {
            return;
        }
        draft = true;
        firstRow = Math.max(0, totalRows() - rows);
        editor.focusDraftSoon();
    }

    // ---------------------------------------------------------------- dica e JEI

    /** Dica sob o cursor, ou null. */
    public @Nullable List<Component> tooltip(double mouseX, double mouseY) {
        return visible ? painter.tooltip(layout, lines(), firstRow, draft, editor, mouseX, mouseY) : null;
    }

    /** Onde o JEI pode soltar itens: o ícone de cada linha visível e o "+" (linha nova). */
    public List<DropTarget> dropTargets() {
        List<DropTarget> targets = new ArrayList<>();
        if (!visible) {
            return targets;
        }
        targets.add(new DropTarget(new Rect2i(layout.addX(), layout.addY(), BUTTON, BUTTON), -1));
        for (int row = 0; row < rows; row++) {
            int index = firstRow + row;
            if (index < totalRows()) {
                targets.add(new DropTarget(new Rect2i(layout.iconX(), layout.rowY(row) + 1, 18, 18),
                        isDraft(index) ? -1 : index));
            }
        }
        return targets;
    }

    /** Item solto pelo JEI: vira linha nova ({@code index = -1}) ou troca o alvo da linha. */
    public void acceptDrop(int index, ItemStack stack) {
        if (index < 0) {
            sender.sendStack(Op.ADD_STACK, -1, stack);
            draft = false;
        } else {
            sender.sendStack(Op.SET_STACK, index, stack);
        }
    }

    /** Limite de linhas da config do servidor (sincronizada com o cliente ao entrar no mundo). */
    static int maxLines() {
        return Config.SPEC.isLoaded() ? Config.LIST_MAX_LINES.get() : TargetList.HARD_MAX_LINES;
    }

    /** Texto da caixa de quantidade ("∞" com "tudo" ligado, e a caixa fica travada). */
    static String amountText(TargetLineView line) {
        return line.all() ? "∞" : String.valueOf(line.amount());
    }
}
