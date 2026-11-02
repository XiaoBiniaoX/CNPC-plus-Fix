package top.cnpcplus.gui;

/**
 * Implemented by MixinGuiDialogModernScroll so preview subclasses can reuse the scrollbar hit test.
 *
 * <p>Deliberately outside {@code top.cnpcplus.mixin}: Mixin rejects every direct reference to a
 * class inside a declared mixin package ("is in a defined mixin package ... and cannot be
 * referenced directly"), which crashed the dialog probe with IllegalClassLoadError.
 */
public interface ModernDialogScrollAccess {
    boolean cnpcplus$scrollbarClick(double mouseX, double mouseY, int button);
}
