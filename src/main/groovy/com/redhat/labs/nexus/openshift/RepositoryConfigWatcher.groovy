package com.redhat.labs.nexus.openshift


import groovy.json.JsonSlurper
import io.kubernetes.client.models.V1ConfigMap
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.sonatype.nexus.blobstore.api.BlobStoreManager
import org.sonatype.nexus.repository.config.Configuration

class RepositoryConfigWatcher {

  private static final Logger LOG = LoggerFactory.getLogger(RepositoryConfigWatcher.class)

  private final RepositoryApi repository
  private BlobStoreManager blobStoreManager

  RepositoryConfigWatcher(RepositoryApi repository, BlobStoreManager blobStoreManager) {
    this.repository = repository
    this.blobStoreManager = blobStoreManager
  }

  static void createNewRepository(RepositoryApi repository, V1ConfigMap configMap) {
    String repositoryName = configMap.data.get("name")
    if (repositoryName != null) {
      def configMapJson = configMap.data.get("config") as String
      JsonSlurper parser = new JsonSlurper();
      def config = parser.parse(configMapJson.getBytes())
      if (repository.getRepositoryManager().exists(repositoryName)) {
        // Repository exists, update it if possible
        def existingRepo = repository.getRepositoryManager().get(repositoryName)
        if (existingRepo.configuration.recipeName == config.recipeName) {
          // Compatible recipes, update existing repository
          existingRepo.attributes = config.attributes
          repository.updateRepository(existingRepo)
        }
      } else {
        // Repository is new, create it if possible
        Configuration repoConfig = new Configuration(
                repositoryName: repositoryName,
                recipeName: config.recipeName,
                online: true,
                attributes: config.attributes
        )
        repository.addRepository(repoConfig)
      }
    } else {
      LOG.warn("Repository name is not set or repository already exists, refusing to recreate existing repository or unnamed repository")
    }
  }
}
