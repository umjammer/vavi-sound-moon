package moonDriver.common;

import java.util.List;
import java.util.StringJoiner;

import dotnet4j.util.compat.Tuple3;


public class GD3 {

    public String TrackName = "";
    public String TrackNameJ = "";
    public String GameName = "";
    public String GameNameJ = "";
    public String SystemName = "";
    public String SystemNameJ = "";
    public String Composer = "";
    public String ComposerJ = "";
    public String Converted = "";
    public String Notes = "";
    public String VGMBy = "";
    public String Version = "";
    public String UsedChips = "";

    public List<Tuple3<Integer, Integer, String>> Lyrics = null;

    @Override
    public String toString() {
        return new StringJoiner(", ", GD3.class.getSimpleName() + "[", "]")
                .add("TrackName='" + TrackName + "'")
                .add("TrackNameJ='" + TrackNameJ + "'")
                .add("GameName='" + GameName + "'")
                .add("GameNameJ='" + GameNameJ + "'")
                .add("SystemName='" + SystemName + "'")
                .add("SystemNameJ='" + SystemNameJ + "'")
                .add("Composer='" + Composer + "'")
                .add("ComposerJ='" + ComposerJ + "'")
                .add("Converted='" + Converted + "'")
                .add("Notes='" + Notes + "'")
                .add("VGMBy='" + VGMBy + "'")
                .add("Version='" + Version + "'")
                .add("UsedChips='" + UsedChips + "'")
                .toString();
    }
}
