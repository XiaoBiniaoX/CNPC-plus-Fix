package top.cnpcplus.mixin;

import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import noppes.npcs.client.gui.script.GuiScriptInterface;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.controllers.IScriptHandler;
import noppes.npcs.shared.client.gui.components.GuiTextArea;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import noppes.npcs.shared.client.gui.listeners.ICustomScrollListener;
import noppes.npcs.controllers.ScriptContainer;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.script.client.ScriptTextArea;
import top.cnpcplus.api.script.ScriptPageApi;
import top.cnpcplus.api.script.client.ScriptEditorClientApi;

@Mixin(value = GuiScriptInterface.class, remap = false)
public abstract class MixinGuiScriptCompletion extends GuiNPCInterface {
    @Shadow private int activeTab;
    @Shadow public IScriptHandler handler;
    @Shadow public List<String> methods;
    @Unique private int cnpcplus$panelWidth, cnpcplus$panelHeight;
    @Unique private ScriptContainer cnpcplus$sessionPage;
    @Unique private String cnpcplus$documentId = UUID.randomUUID().toString();
    @Unique private long cnpcplus$documentRevision = 1;
    @Unique private List<ScriptPageApi.SourceUnit> cnpcplus$lastUnits = List.of();
    @Unique private List<ScriptPageApi.SourceProblem> cnpcplus$lastProblems = List.of();
    @Unique private String cnpcplus$lastLanguage = "";
    @Unique private boolean cnpcplus$hasSnapshot;
    @Unique private String cnpcplus$editorProblem = "";
    @Unique private ScriptEditorClientApi.EditorSession cnpcplus$session;

    // Set the real panel dimensions before native controls are built; the native
    // tail resets imageWidth/Height for its scaled texture, not for layout.
    @Inject(method = "m_7856_()V", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/client/gui/util/GuiNPCInterface;m_7856_()V"), require = 1)
    private void cnpcplus$fitPanel(CallbackInfo ci) {
        imageWidth = Math.min(imageWidth, width - 16);
        imageHeight = Math.min(height - 34, Math.max(190, imageHeight));
        cnpcplus$panelWidth = imageWidth;
        cnpcplus$panelHeight = imageHeight;
    }

    @Inject(method = "m_7856_()V", at = @At("TAIL"), require = 1)
    private void cnpcplus$addSimpleCheck(CallbackInfo ci) {
        imageWidth = cnpcplus$panelWidth;
        imageHeight = cnpcplus$panelHeight;
        if (get(3) instanceof ScriptTextArea editor) {
            int right = guiLeft + imageWidth - 6;
            int left = editor.x + editor.width + 4;
            int sideWidth = right - left;
            for (int id : new int[]{99, 102, 101, 100, 105, 107}) {
                var side = getButton(id);
                if (side == null) continue;
                boolean second = id == 101 || id == 105;
                boolean half = id == 100 || id == 101 || id == 102 || id == 105;
                int halfWidth = (sideWidth - 2) / 2;
                side.setX(left + (second ? halfWidth + 2 : 0));
                side.setWidth(half ? (second ? sideWidth - halfWidth - 2 : halfWidth) : sideWidth);
            }
            int bottom = guiTop + imageHeight - 8;
            for (int id = 0; id <= 2; id++) {
                var scroll = getScroll(id);
                if (scroll == null) continue;
                scroll.guiLeft = left;
                if (id == 0) scroll.setSize(sideWidth, bottom - scroll.guiTop);
                else if (id == 1) scroll.setSize(sideWidth, (bottom - scroll.guiTop - 4) * 2 / 3);
                else {
                    var functions = getScroll(1);
                    scroll.guiTop = functions.guiTop + functions.getHeight() + 4;
                    scroll.setSize(sideWidth, bottom - scroll.guiTop);
                }
            }
            cnpcplus$prepareSession();
            int actionTop = getScroll(1) != null && getScroll(1).visible
                    ? getScroll(1).guiTop : getScroll(0) == null ? guiTop + 88 : getScroll(0).guiTop;
            int firstRow = actionTop;
            int rows = 0;
            var slot = new ScriptEditorClientApi.Area(left, firstRow, sideWidth, 20);
            var languageActions = ScriptEditorClientApi.actions(
                    ScriptEditorClientApi.Placement.LANGUAGE_ACTION, cnpcplus$session, slot);
            int capacity = bottom - firstRow < 20 ? 0 : 1 + (bottom - firstRow - 20) / 22;
            int buttonCapacity = Math.max(0, sideWidth / 44);
            languageActions = languageActions.stream().limit(capacity == 0 ? 0 : buttonCapacity).toList();
            if (!languageActions.isEmpty()) {
                cnpcplus$addActionRow(languageActions, firstRow, left, sideWidth, 600);
                rows++;
            }
            var sidebarSlot = new ScriptEditorClientApi.Area(left, firstRow + rows * 22, sideWidth, 20);
            var sidebarActions = ScriptEditorClientApi.actions(
                    ScriptEditorClientApi.Placement.SIDEBAR_ACTION, cnpcplus$session, sidebarSlot);
            int remainingRows = Math.max(0, capacity - rows);
            sidebarActions = sidebarActions.stream().limit(remainingRows == 0 ? 0 : buttonCapacity).toList();
            if (!sidebarActions.isEmpty()) {
                cnpcplus$addActionRow(sidebarActions, firstRow + rows * 22, left, sideWidth, 620);
                rows++;
            }
            if (rows > 0) {
                for (int id : new int[]{0, 1}) {
                    var scroll = getScroll(id);
                    if (scroll != null && scroll.visible) scroll.guiTop += rows * 22;
                }
                var links = getScroll(0);
                if (links != null && links.visible) links.setSize(sideWidth, bottom - links.guiTop);
                var functions = getScroll(1);
                if (functions != null && functions.visible) {
                    functions.setSize(sideWidth, (bottom - functions.guiTop - 4) * 2 / 3);
                    var globals = getScroll(2);
                    if (globals != null) {
                        globals.guiTop = functions.guiTop + functions.getHeight() + 4;
                        globals.setSize(sideWidth, bottom - globals.guiTop);
                    }
                }
            }
            var functions = getScroll(1);
            if (functions != null && functions.visible) {
                functions.listener = new ICustomScrollListener() {
                    @Override public void scrollClicked(double x, double y, int button, GuiCustomScrollNop scroll) {}
                    @Override public void scrollDoubleClicked(String selection, GuiCustomScrollNop scroll) {
                        ScriptTextArea area = get(3) instanceof ScriptTextArea value ? value : null;
                        ScriptPageApi.HookDescriptor hook = cnpcplus$session.context().hooks().stream()
                                .filter(candidate -> candidate.name().equals(selection)).findFirst().orElse(null);
                        if (area == null || !area.insertTemplate(hook)) {
                            if (area != null) area.addText(selection);
                        }
                    }
                };
            }
            int buttonWidth = (editor.width - 4) / 2;
            int y = editor.y + editor.height + 26;
            addButton(new GuiButtonNop(this, 422, editor.x, y, buttonWidth, 20, "cnpcplus.api.button",
                    pressed -> setSubGui(new top.cnpcplus.script.client.SubGuiApiDocs())));
            GuiButtonNop button = new GuiButtonNop(this, 421,
                    editor.x + buttonWidth + 4, y,
                    editor.width - buttonWidth - 4, 20, "cnpcplus.script.simple_check", pressed -> editor.simpleCheck());
            addButton(button);
        }
    }

    @Unique
    private void cnpcplus$prepareSession() {
        ScriptContainer page = cnpcplus$page();
        if (page != cnpcplus$sessionPage) {
            cnpcplus$sessionPage = page;
            cnpcplus$documentId = UUID.randomUUID().toString();
            cnpcplus$documentRevision = 1;
            cnpcplus$lastUnits = List.of();
            cnpcplus$lastProblems = List.of();
            cnpcplus$lastLanguage = "";
            cnpcplus$hasSnapshot = false;
        }
        cnpcplus$session = new ScriptEditorClientApi.EditorSession() {
            @Override public ScriptEditorClientApi.EditorContext context() {
                ScriptContainer current = cnpcplus$page();
                if (current == null) return new ScriptEditorClientApi.EditorContext(
                        ScriptPageApi.Carrier.UNKNOWN, "", "", "", List.of());
                ScriptPageApi.EditorDescriptor descriptor = cnpcplus$editorDescriptor(current);
                return new ScriptEditorClientApi.EditorContext(descriptor.carrier(), descriptor.defaultLanguage(),
                        descriptor.overrideLanguage(), descriptor.effectiveLanguage(), descriptor.hooks());
            }

            @Override public ScriptPageApi.SourceBundle snapshot() {
                flushDraft();
                ScriptContainer current = cnpcplus$page();
                if (current == null) throw new ScriptPageApi.PageUnavailableException();
                String language = cnpcplus$editorDescriptor(current).effectiveLanguage();
                String text = get(3) instanceof ScriptTextArea area ? area.getText() : current.script;
                ScriptPageApi.SourceBundle bundle = ScriptPageApi.draftSources(current, handler,
                        cnpcplus$documentId, cnpcplus$documentRevision, text);
                if (cnpcplus$hasSnapshot && (!bundle.units().equals(cnpcplus$lastUnits)
                        || !bundle.problems().equals(cnpcplus$lastProblems) || !language.equals(cnpcplus$lastLanguage))) {
                    cnpcplus$documentRevision++;
                }
                cnpcplus$hasSnapshot = true;
                cnpcplus$lastUnits = bundle.units();
                cnpcplus$lastProblems = bundle.problems();
                cnpcplus$lastLanguage = language;
                return new ScriptPageApi.SourceBundle(cnpcplus$documentId, cnpcplus$documentRevision,
                        bundle.units(), bundle.problems());
            }

            @Override public void flushDraft() {
                ScriptContainer current = cnpcplus$page();
                if (current != null && get(3) instanceof ScriptTextArea area)
                    current.script = area.getText().replace("\r\n", "\n").replace('\r', '\n');
            }

            @Override public boolean requestSave() {
                if (cnpcplus$page() == null) return false;
                flushDraft();
                MixinGuiScriptCompletion.this.save();
                return true;
            }

            @Override public void refresh() {
                flushDraft();
                MixinGuiScriptCompletion.this.m_7856_();
            }

            @Override public void openSubGui(noppes.npcs.shared.client.gui.components.GuiBasic screen) {
                flushDraft();
                MixinGuiScriptCompletion.this.setSubGui(screen);
            }
        };
    }

    @Unique
    private ScriptPageApi.EditorDescriptor cnpcplus$editorDescriptor(ScriptContainer page) {
        ScriptPageApi.Carrier carrier = ScriptPageApi.carrier(handler);
        List<String> names = methods == null ? List.of() : List.copyOf(methods);
        try {
            ScriptPageApi.EditorDescriptor descriptor = ScriptPageApi.describeEditor(page, handler, carrier, names);
            cnpcplus$editorProblem = "";
            return descriptor;
        } catch (ScriptPageApi.LanguageConflictException conflict) {
            cnpcplus$editorProblem = "cnpcplus.script.language_conflict";
            return new ScriptPageApi.EditorDescriptor(carrier, handler.getLanguage(), "", "",
                    ScriptPageApi.hooks(names, carrier));
        } catch (RuntimeException | LinkageError unavailable) {
            cnpcplus$editorProblem = "cnpcplus.script.language_unavailable";
            return new ScriptPageApi.EditorDescriptor(carrier, handler.getLanguage(), "", "",
                    ScriptPageApi.hooks(names, carrier));
        }
    }

    @Unique
    private ScriptContainer cnpcplus$page() {
        return handler != null && activeTab > 0 && activeTab <= handler.getScripts().size()
                ? handler.getScripts().get(activeTab - 1) : null;
    }

    @Unique
    private void cnpcplus$addActionRow(List<ScriptEditorClientApi.Action> actions, int y, int x, int width, int baseId) {
        int buttonWidth = (width - Math.max(0, actions.size() - 1) * 2) / actions.size();
        for (int i = 0; i < actions.size(); i++) {
            ScriptEditorClientApi.Action action = actions.get(i);
            int id = baseId + i;
            int buttonX = x + i * (buttonWidth + 2);
            addButton(new GuiButtonNop(this, id, buttonX, y, buttonWidth, 20, action.label(), button -> {
                cnpcplus$session.flushDraft();
                try { action.onPress().accept(cnpcplus$session); }
                catch (RuntimeException | LinkageError failure) {
                    org.slf4j.LoggerFactory.getLogger("cnpcplus.script").warn("Editor action failed", failure);
                }
            }));
        }
    }

    @Override
    public void m_88315_(GuiGraphics g, int mx, int my, float partial) {
        if (drawDefaultBackground && !hasSubGui()) renderBackground(g);
        g.fill(guiLeft, guiTop, guiLeft + imageWidth, guiTop + imageHeight, 0xFF555555);
        g.fill(guiLeft, guiTop, guiLeft + imageWidth - 2, guiTop + imageHeight - 2, 0xFFFFFFFF);
        g.fill(guiLeft + 2, guiTop + 2, guiLeft + imageWidth - 2, guiTop + imageHeight - 2, 0xFFC6C6C6);
        var texture = background;
        boolean dim = drawDefaultBackground;
        background = null;
        drawDefaultBackground = false;
        super.m_88315_(g, mx, my, partial);
        background = texture;
        drawDefaultBackground = dim;
    }

    @Redirect(
            method = "m_7856_()V",
            at = @At(value = "NEW", target = "noppes/npcs/shared/client/gui/components/GuiTextArea"),
            require = 2
    )
    private GuiTextArea cnpcplus$scriptEditor(int id, int x, int y, int width, int height, String text) {
        ScriptContainer page = cnpcplus$page();
        if (page == null) {
            return new GuiTextArea(id, x, y, width, height, text);
        }
        ScriptPageApi.EditorDescriptor descriptor = cnpcplus$editorDescriptor(page);
        String context = switch (descriptor.carrier()) {
            case PLAYER -> "PlayerEvent";
            case BLOCK -> "BlockEvent";
            case BLOCK_DOOR -> "BlockDoorEvent";
            case ITEM -> "ItemEvent";
            case FORGE -> "ForgeEvent";
            case NPC -> "NpcEvent";
            case UNKNOWN -> "";
        };
        ScriptTextArea editor = new ScriptTextArea(id, x, y, width - 18, height, text, page, context,
                descriptor.effectiveLanguage(), descriptor.hooks().stream().map(ScriptPageApi.HookDescriptor::name).toList(),
                descriptor.carrier());
        if (!cnpcplus$editorProblem.isEmpty()) editor.setEditorProblem(cnpcplus$editorProblem);
        return editor;
    }

    // GuiWrapper closes the screen on Esc before dispatching to components; dismiss completion first.
    @Override
    public boolean m_7933_(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && !hasSubGui() && get(3) instanceof ScriptTextArea editor
                && editor.hasPopup()) {
            return editor.m_7933_(keyCode, scanCode, modifiers);
        }
        return super.m_7933_(keyCode, scanCode, modifiers);
    }
}
