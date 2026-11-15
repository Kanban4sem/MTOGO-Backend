package mtogo.feature.user.util;

import java.util.Locale;

public final class EmailUtil
{
    private EmailUtil()
    {
    }

    public static String normalize(String email)
    {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
