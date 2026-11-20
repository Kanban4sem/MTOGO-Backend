package mtogo.testsupport;

import java.util.ArrayList;
import java.util.List;

/**
 * A startup failure wraps the exception that caused it, often several times, so
 * tests look through the whole cause chain.
 */
public final class CauseUtil
{
    private CauseUtil()
    {
    }

    public static List<String> causeNames(Throwable failure)
    {
        List<String> names = new ArrayList<>();
        for (Throwable cause = failure; cause != null; cause = cause.getCause())
        {
            names.add(cause.getClass().getSimpleName());
        }
        return names;
    }
}
