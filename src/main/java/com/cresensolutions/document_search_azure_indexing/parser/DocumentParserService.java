package com.cresensolutions.document_search_azure_indexing.parser;

import com.cresensolutions.document_search_azure_indexing.dto.DownloadedBlob;
import com.cresensolutions.document_search_azure_indexing.dto.ParsedDocument;

public interface DocumentParserService {
    ParsedDocument parse(DownloadedBlob downloadedBlob);
}
