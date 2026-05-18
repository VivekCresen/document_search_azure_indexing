# document_search_azure_indexing

Spring Boot service scaffold for Azure AI Search indexing.

## Run

```bash
./mvnw spring-boot:run
```

The service starts on port `8086`.

## Local Configuration

Create a `.env` file in this folder or the workspace root:

```properties
AZURE_SEARCH_ENDPOINT=
AZURE_SEARCH_ADMIN_KEY=
AZURE_SEARCH_INDEX_NAME=document-index
AZURE_STORAGE_ACCOUNT_NAME=
AZURE_STORAGE_ACCOUNT_KEY=
AZURE_STORAGE_ACCOUNT_URL=
AZURE_STORAGE_CONTAINER_NAME=
AZURE_OPENAI_ENDPOINT=
AZURE_OPENAI_KEY=
AZURE_OPENAI_EMBEDDING_DEPLOYMENT=text-embedding-3-small
AZURE_OPENAI_DEPLOYMENT=gpt-4o-mini
```

Initial check endpoint:

```text
GET /api/indexing/info
```
