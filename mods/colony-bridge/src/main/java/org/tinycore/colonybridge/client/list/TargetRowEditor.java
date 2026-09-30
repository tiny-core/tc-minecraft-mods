package org.tinycore.colonybridge.client.list;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.tinycore.colonybridge.logic.target.TargetKind;
import org.tinycore.colonybridge.logic.target.TargetListKind;
import org.tinycore.colonybridge.logic.target.TargetResolver;
import org.tinycore.colonybridge.logic.target.TargetSpec;
import org.tinycore.colonybridge.menu.TargetLineView;
import org.tinycore.colonybridge.network.TargetEditPayload.Op;
import org.tinycore.core.client.ui.ScreenStyle;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static org.tinycore.colonybridge.client.list.TargetRowLayout.AMOUNT_WIDTH;

/**
 * As caixas de texto da {@link TargetListWidget}: uma de alvo e uma de quantidade por linha visível.
 * <ul>
 *   <li><b>ligar às linhas:</b> ao rolar ou quando chega uma lista nova, cada caixa recebe o valor da linha que
 *       está mostrando — menos a que está em foco, para não apagar o que o jogador digita;</li>
 *   <li><b>confirmar:</b> Enter ou sair da caixa manda a edição; texto igual ao atual não manda nada;</li>
 *   <li><b>validar na hora:</b> texto que não é um alvo válido, não existe no modpack ou não é aceito nesta
 *       lista fica vermelho e nunca é enviado (o servidor valida de novo).</li>
 * </ul>
 * Também guarda o item de origem de cada linha para o clique direito no ícone ({@link #cycleTag}).
 */
final class TargetRowEditor {

    private final TargetListKind kind;
    private final TargetEditSender sender;
    private final int rows;
    private final EditBox[] texts;
    private final EditBox[] amounts;
    private final boolean[] textFocused;
    private final boolean[] amountFocused;
    /** Linha (índice na lista) que cada caixa mostra; -2 = nada ligado ainda. */
    private final int[] bound;
    /** Item de onde o jogador começou a alternar tags (clique direito), por índice da linha. */
    private final Map<Integer, ItemStack> origins = new HashMap<>();

    private Consumer<GuiEventListener> focus = listener -> {};
    private int boundRevision = -1;
    private boolean focusDraft;
    private boolean visible = true;
    /** Estado do último {@link #tick}, para os cliques e o Enter saberem o que cada caixa mostra. */
    private List<TargetLineView> lines = List.of();
    private int firstRow;
    private boolean draft;

    TargetRowEditor(TargetListKind kind, TargetEditSender sender, int rows) {
        this.kind = kind;
        this.sender = sender;
        this.rows = rows;
        this.texts = new EditBox[rows];
        this.amounts = new EditBox[rows];
        this.textFocused = new boolean[rows];
        this.amountFocused = new boolean[rows];
        this.bound = new int[rows];
    }

    void init(Font font, TargetRowLayout layout, UnaryOperator<EditBox> add, Consumer<GuiEventListener> focus) {
        this.focus = focus;
        for (int row = 0; row < rows; row++) {
            int y = layout.rowY(row) + 6;
            texts[row] = add.apply(box(font, layout.textX() + 3, y, layout.textWidth() - 6, TargetSpec.MAX_TEXT,
                    "gui.tccolonybridge.list.syntax"));
            amounts[row] = add.apply(box(font, layout.amountX() + 3, y, AMOUNT_WIDTH - 6, 5,
                    "gui.tccolonybridge.list.amount"));
            textFocused[row] = false;
            amountFocused[row] = false;
            bound[row] = -2;
        }
        boundRevision = -1;
    }

    private static EditBox box(Font font, int x, int y, int width, int maxLength, String hintKey) {
        EditBox box = new EditBox(font, x, y, width, 10, Component.translatable(hintKey));
        box.setBordered(false);
        box.setMaxLength(maxLength);
        box.visible = false;
        return box;
    }

    void setVisible(boolean value) {
        visible = value;
        if (!value) {
            unfocusAll();
        }
        for (int row = 0; row < rows && texts[row] != null; row++) {
            texts[row].visible = false; // o próximo tick mostra as caixas que têm linha
            amounts[row].visible = false;
            bound[row] = -2;
        }
        boundRevision = -1;
    }

    // ---------------------------------------------------------------- tick

    /** @return true se o rascunho foi confirmado neste tick (o widget fecha o rascunho) */
    boolean tick(List<TargetLineView> lines, int firstRow, boolean draft, int revision) {
        boolean draftCommitted = false;
        for (int row = 0; row < rows; row++) {
            boolean text = texts[row].isFocused();
            if (textFocused[row] && !text) {
                draftCommitted |= commitText(row);
            }
            textFocused[row] = text;
            boolean amount = amounts[row].isFocused();
            if (amountFocused[row] && !amount) {
                commitAmount(row);
            }
            amountFocused[row] = amount;
        }
        this.lines = lines;
        this.firstRow = firstRow;
        this.draft = draft && !draftCommitted;
        bind(revision);
        return draftCommitted;
    }

    private void bind(int revision) {
        boolean newData = revision != boundRevision;
        boundRevision = revision;
        for (int row = 0; row < rows; row++) {
            int index = firstRow + row;
            boolean isLine = visible && index < lines.size();
            boolean isDraft = visible && draft && index == lines.size();
            EditBox text = texts[row];
            EditBox amount = amounts[row];
            if (newData || bound[row] != index || (!isLine && !isDraft)) {
                if (isLine && !text.isFocused()) {
                    text.setValue(lines.get(index).text());
                } else if (isDraft && bound[row] != index) {
                    text.setValue("");
                }
                if (isLine && !amount.isFocused()) {
                    TargetLineView line = lines.get(index);
                    amount.setValue(TargetListWidget.amountText(line));
                    amount.setEditable(!line.all());
                }
                bound[row] = index;
            }
            text.visible = isLine || isDraft;
            amount.visible = isLine && kind.hasAmount();
            text.setTextColor(problem(text.getValue()) == null ? ScreenStyle.TEXT : ScreenStyle.DANGER);
            if (isDraft && focusDraft) {
                focusDraft = false;
                text.setFocused(true);
                focus.accept(text);
            }
        }
    }

    // ---------------------------------------------------------------- confirmar

    /** @return true se confirmou o rascunho */
    private boolean commitText(int row) {
        int index = firstRow + row;
        String text = texts[row].getValue().trim();
        if (draft && index == lines.size()) {
            if (text.isEmpty() || problem(text) != null) {
                return false; // continua rascunho (vermelho se inválido)
            }
            sender.sendText(Op.ADD_TEXT, -1, text);
            texts[row].setValue("");
            return true;
        }
        if (index >= lines.size()) {
            return false;
        }
        TargetLineView line = lines.get(index);
        if (text.isEmpty()) {
            texts[row].setValue(line.text()); // apagar tudo = desistir; remover é no "x"
        } else if (!text.equals(line.text()) && problem(text) == null) {
            sender.sendText(Op.SET_TEXT, index, text);
            origins.remove(index);
        }
        return false;
    }

    private void commitAmount(int row) {
        int index = firstRow + row;
        if (index >= lines.size() || lines.get(index).all()) {
            return;
        }
        TargetLineView line = lines.get(index);
        try {
            int value = Math.max(0, Math.min(TargetListKind.MAX_AMOUNT, Integer.parseInt(amounts[row].getValue().trim())));
            if (value != line.amount()) {
                sender.sendAmount(index, value, false);
            }
        } catch (NumberFormatException e) {
            amounts[row].setValue(TargetListWidget.amountText(line)); // não é número: volta ao valor atual
        }
    }

    /** Motivo de o texto não valer nesta lista, ou null se vale. */
    @Nullable Component problem(String text) {
        String clean = text.trim();
        if (clean.isEmpty()) {
            return null;
        }
        TargetSpec spec = TargetSpec.parse(clean);
        if (spec == null) {
            return Component.translatable("gui.tccolonybridge.list.bad_syntax");
        }
        if (!kind.allows(spec.kind())) {
            return Component.translatable("gui.tccolonybridge.list.not_here");
        }
        if (!TargetResolver.exists(spec)) {
            return Component.translatable("gui.tccolonybridge.list.missing", spec.text());
        }
        return null;
    }

    /** Dica da caixa de texto sob o cursor quando o texto é inválido. */
    @Nullable List<Component> problemAt(double mouseX, double mouseY) {
        for (EditBox text : texts) {
            if (text.visible && text.isMouseOver(mouseX, mouseY)) {
                Component problem = problem(text.getValue());
                return problem == null ? null : List.of(problem);
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- tags do item (clique direito)

    /**
     * Alterna o alvo entre o item e as tags dele: item → 1ª tag → 2ª tag → ... → item de novo. O item de origem
     * fica guardado para voltar a ele (com os componentes) no fim da volta.
     */
    void cycleTag(int index, TargetLineView line) {
        TargetSpec spec = line.spec();
        if (spec == null || spec.kind() == TargetKind.MOD) {
            return;
        }
        ItemStack origin = origins.getOrDefault(index,
                spec.kind() == TargetKind.ITEM ? line.item() : TargetIcons.icon(spec, line.item()).copy());
        if (origin.isEmpty()) {
            return;
        }
        List<String> tags = origin.getTags().map(TagKey::location).map(Object::toString)
                .sorted(Comparator.naturalOrder()).toList();
        if (tags.isEmpty()) {
            return;
        }
        int position = spec.kind() == TargetKind.TAG ? tags.indexOf(spec.id()) : -1;
        if (position + 1 < tags.size()) {
            origins.put(index, origin);
            sender.sendText(Op.SET_TEXT, index, TargetKind.TAG.prefix() + tags.get(position + 1));
        } else {
            origins.remove(index);
            sender.sendStack(Op.SET_STACK, index, origin);
        }
    }

    /** Uma linha foi removida: os índices mudam, então as origens guardadas não valem mais. */
    void forgetOrigins() {
        origins.clear();
    }

    // ---------------------------------------------------------------- foco e teclado

    void focusDraftSoon() {
        focusDraft = true;
    }

    boolean isOverBox(double mouseX, double mouseY) {
        for (int row = 0; row < rows; row++) {
            if ((texts[row].visible && texts[row].isMouseOver(mouseX, mouseY))
                    || (amounts[row].visible && amounts[row].isMouseOver(mouseX, mouseY))) {
                return true;
            }
        }
        return false;
    }

    boolean isEditing() {
        for (int row = 0; row < rows; row++) {
            if (texts[row].isFocused() || amounts[row].isFocused()) {
                return true;
            }
        }
        return false;
    }

    /** Tira o foco de todas as caixas; o próximo tick confirma o que foi digitado. */
    void unfocusAll() {
        for (int row = 0; row < rows && texts[row] != null; row++) {
            texts[row].setFocused(false);
            amounts[row].setFocused(false);
        }
        focus.accept(null);
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (int row = 0; row < rows; row++) {
            EditBox box = texts[row].isFocused() ? texts[row] : amounts[row].isFocused() ? amounts[row] : null;
            if (box == null) {
                continue;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                    || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                unfocusAll(); // o próximo tick confirma (Esc também: o texto inválido fica vermelho)
            } else {
                box.keyPressed(keyCode, scanCode, modifiers);
            }
            return true;
        }
        return false;
    }
}
