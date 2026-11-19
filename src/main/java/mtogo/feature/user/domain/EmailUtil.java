package mtogo.feature.user.domain;

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
