package com.cresensolutions.document_search_azure_indexing.commons;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Constructor;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CommonsTest {

    @Test
    void testCommonConstructor() throws Exception {
        Constructor<Common> constructor = Common.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Common instance = constructor.newInstance();
        assertNotNull(instance);
    }

    @Test
    void testConstantsConstructor() throws Exception {
        Constructor<Constants> constructor = Constants.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Constants instance = constructor.newInstance();
        assertNotNull(instance);
    }
}
