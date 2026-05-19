package com.cresensolutions.document_search_azure_indexing;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.mockito.Mockito.mockStatic;

class ApplicationClassTest {

    @Test
    void testMainMethod() {
        try (MockedStatic<SpringApplication> springAppMock = mockStatic(SpringApplication.class)) {
            springAppMock.when(() -> SpringApplication.run(DocumentSearchAzureIndexingApplication.class, new String[]{}))
                    .thenReturn(null);

            DocumentSearchAzureIndexingApplication.main(new String[]{});

            springAppMock.verify(() -> SpringApplication.run(DocumentSearchAzureIndexingApplication.class, new String[]{}));
        }
    }
}
