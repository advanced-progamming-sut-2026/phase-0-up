package utils.gameinitializers.parsers;

import java.util.List;

public interface Parser<T> {
    List<T> parse(String filePath);
}
