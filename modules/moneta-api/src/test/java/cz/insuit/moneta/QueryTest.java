package cz.insuit.moneta;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class QueryTest {

    @Test
    void appendsParametersInOrderAndEncodesThem() {
        var query = Query.NONE.with("fromDate", "2026-08-01").with("note", "a b&c");
        assertEquals("accounts?fromDate=2026-08-01&note=a%20b%26c", query.appendTo("accounts"));
    }

    @Test
    void leavesThePathAloneWithoutParameters() {
        assertEquals("accounts", Query.NONE.appendTo("accounts"));
    }

    @Test
    void doesNotChangeTheQueryItExtends() {
        var base = Query.NONE.with("size", 100);
        base.with("page", 1);
        assertEquals("t?size=100", base.appendTo("t"));
    }

    @Test
    void encodesAnAccountIdAsOnePathSegment() {
        assertEquals("api/v4/vip/aisp/my/accounts/a%2Fb%20c/transactions", MonetaPaths.transactions("a/b c"));
    }
}
