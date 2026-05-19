package com.cresensolutions.document_search_azure_indexing.commons;

public class Common {

    protected Common() {
    }

    // Job Statuses
    public static final String JOB_STATUS_TO_BE_INGESTED = "to_be_ingested";
    public static final String JOB_STATUS_INGESTION_INP = "ingestion_inp";
    public static final String JOB_STATUS_TO_BE_DELETED = "to_be_deleted";
    public static final String JOB_STATUS_DELETE_INP = "delete_inp";
    public static final String JOB_STATUS_STABLE = "stable";
    public static final String JOB_STATUS_DELETED = "deleted";
    public static final String JOB_STATUS_FAILED = "failed";

    // Audit Actions
    public static final String AUDIT_ACTION_INDEX_EXECUTION = "INDEX_EXECUTION";
    public static final String AUDIT_ACTION_DELETE_EXECUTION = "DELETE_EXECUTION";

    // Cache Names
    public static final String CACHE_FOLDER_RESOLUTION = "folderResolution";

    // Common Strings
    public static final String HOSTNAME_FALLBACK = "document-search-azure-indexing";
}
