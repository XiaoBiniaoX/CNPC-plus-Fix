package bin.cnpcplus.lines;

import noppes.npcs.controllers.data.Line;

/** Stable native single-sound API; playback receives a detached, randomly selected sound. */
public final class ArrayLine extends Line {
    public SoundArray sounds = new SoundArray();
    public String spans = "";
    public static ArrayLine of(Line line) {
        if (line instanceof ArrayLine) return (ArrayLine) line;
        ArrayLine copy = new ArrayLine();
        copy.setText(line.getText());
        copy.setSound(line.getSound());
        copy.setShowText(line.getShowText());
        return copy;
    }
    @Override public String getSound() { return sounds.first(); }
    @Override public void setSound(String value) { sounds.single(value); }
    @Override public ArrayLine copy() {
        ArrayLine copy = new ArrayLine();
        copy.setText(getText());
        copy.setShowText(getShowText());
        copy.sounds = sounds.copy();
        copy.spans = spans;
        return copy;
    }
    public Line forPlayback() {
        ArrayLine copy = copy();
        copy.setSound(sounds.choose());
        return copy;
    }
}
