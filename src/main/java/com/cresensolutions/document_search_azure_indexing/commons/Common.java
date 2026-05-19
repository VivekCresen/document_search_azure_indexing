package com.cresensolutions.document_search_azure_indexing.commons;

/**
 * Common base class defining job status, audit actions, caches, and system fallback strings
 * used globally across the indexing microservice.
 */
public class Common {

    protected Common() {
    }

    /** Ingestion job queued but not yet processed */
    public static final String JOB_STATUS_TO_BE_INGESTED = "to_be_ingested";
    /** Ingestion job currently in progress */
    public static final String JOB_STATUS_INGESTION_INP = "ingestion_inp";
    /** Job marked for deletion from search index */
    public static final String JOB_STATUS_TO_BE_DELETED = "to_be_deleted";
    /** Deletion processing is currently in progress */
    public static final String JOB_STATUS_DELETE_INP = "delete_inp";
    /** Document has been successfully indexed and is stable */
    public static final String JOB_STATUS_STABLE = "stable";
    /** Document is deleted from both search indexes and tracking records */
    public static final String JOB_STATUS_DELETED = "deleted";
    /** Processing failed and retry limit has been exceeded */
    public static final String JOB_STATUS_FAILED = "failed";

    /** Audit log action for document indexing execution */
    public static final String AUDIT_ACTION_INDEX_EXECUTION = "INDEX_EXECUTION";
    /** Audit log action for document deletion execution */
    public static final String AUDIT_ACTION_DELETE_EXECUTION = "DELETE_EXECUTION";

    /** Name of the folder resolution cache descriptor */
    public static final String CACHE_FOLDER_RESOLUTION = "folderResolution";

    /** Fallback hostname indicator for job locks in case network resolution fails */
    public static final String HOSTNAME_FALLBACK = "document-search-azure-indexing";
}
