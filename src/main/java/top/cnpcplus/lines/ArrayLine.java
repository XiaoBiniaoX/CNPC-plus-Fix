package top.cnpcplus.lines;

import noppes.npcs.controllers.data.Line;
import top.cnpcplus.text.StyledText;
import top.cnpcplus.text.TextStyles;

/** Native Line API remains a stable single-sound view; playback uses a private copy. */
public final class ArrayLine extends Line {
    public SoundArray sounds = new SoundArray();

    public static ArrayLine of(Line line) {
        if (line instanceof ArrayLine array) return array;
        ArrayLine out = new ArrayLine();
        out.setText(line.getText());
        out.setSound(line.getSound());
        out.setShowText(line.getShowText());
        out.styled(TextStyles.get(line, TextStyles.LINE, line.getText()).legacy());
        return out;
    }

    @Override public String getSound() { return sounds.first(); }
    @Override public void setSound(String value) { sounds.single(value); }

    public StyledText styled() { return TextStyles.get(this, TextStyles.LINE, getText()); }
    public void styled(StyledText value) { setText(value.text()); TextStyles.put(this, TextStyles.LINE, value); }

    @Override public ArrayLine copy() {
        ArrayLine copy = new ArrayLine();
        copy.setText(getText());
        copy.setShowText(getShowText());
        copy.sounds = sounds.copy();
        copy.styled(styled());
        return copy;
    }

    public Line forPlayback() {
        Line copy = new Line(getText());
        copy.setShowText(getShowText());
        copy.setSound(sounds.choose());
        TextStyles.put(copy, TextStyles.LINE, styled());
        return copy;
    }
}
