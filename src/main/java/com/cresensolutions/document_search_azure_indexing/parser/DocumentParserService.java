package com.cresensolutions.document_search_azure_indexing.parser;

import com.cresensolutions.document_search_azure_indexing.dto.DownloadedBlob;
import com.cresensolutions.document_search_azure_indexing.dto.ParsedDocument;

/**
 * Service interface for parsing text and metadata from downloaded blobs.
 * Handles fallbacks between Azure Document Intelligence and local Apache Tika parser.
 */
public interface DocumentParserService {

    /**
     * Parses the text contents and metadata from the specified downloaded blob.
     *
     * @param downloadedBlob the local reference of the downloaded storage blob
     * @return the parsed document object holding text, title, and metadata Map
     */
    ParsedDocument parse(DownloadedBlob downloadedBlob);
}
