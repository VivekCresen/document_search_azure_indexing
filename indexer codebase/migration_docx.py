from azure.storage.blob import BlobServiceClient
from indexer_config import config

KEEP_BLOBS = {
    "highlighted_docs/Lundbecks_Field_Concierge_FC_AI_Chatbot_Policy_Sales_Interaction_and_Engagement_Data_Usage_Policy.pdf",
    "highlighted_docs/Lundbecks_HCP_AI_Chatbot_Policy_Vyepti_Product_Website_Governance_and_Compliance_Policy.pdf",
    "highlighted_docs/Lundbecks_AI_Sales_Training_Policy.pdf",
}

blob_service = BlobServiceClient.from_connection_string(config.blob_storage.connection_string)
container_client = blob_service.get_container_client(config.blob_storage.container_name)

print("🔍 Scanning highlighted_docs/ for stale Lundbeck blobs...")
for blob in container_client.list_blobs(name_starts_with="highlighted_docs/"):
    if "lundbeck" not in blob.name.lower():
        continue
    if blob.name in KEEP_BLOBS:
        print(f"   ✅ Keeping : {blob.name}")
        continue
    print(f"   🗑️  Deleting: {blob.name}")
    blob_service.get_blob_client(
        container=config.blob_storage.container_name,
        blob=blob.name
    ).delete_blob()

print("\n🎉 Done.")