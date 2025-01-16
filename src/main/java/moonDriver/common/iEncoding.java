package moonDriver.common;

public interface iEncoding {

    String GetStringFromSjisArray(byte[] sjisArray);

    String GetStringFromSjisArray(byte[] sjisArray, int index, int count);

    byte[] GetSjisArrayFromString(String utfString);

    String GetStringFromUtfArray(byte[] utfArray);

    byte[] GetUtfArrayFromString(String utfString);

}
