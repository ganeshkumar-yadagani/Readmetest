# --------------------------------------------------------
# Shared templates for secret scanning & container scanning
# --------------------------------------------------------
include:
  - project: 'tmobile/templates'
    file: '/gitlab-ci/.sdp.container-secret-scan.gitlab-ci.yml'
  - project: 'tmobile/templates'
    file: '/gitlab-ci/.sdp/container-scanning.latest.gitlab-ci.yml'

# --------------------------------------------------------
# Global Variables
# --------------------------------------------------------
variables:
  DOCKER_CUSTOM_BUILD_CONTEXT: ""         # Optional build context, default empty
  DOCKERFILE_PATH: 'Dockerfile'           # Default Dockerfile
  DOCKER_TAG: "latest"                    # Default tag (overridable in jobs)
  CS_IMAGE: "$CI_REGISTRY_IMAGE:latest"   # Default scanning image

# --------------------------------------------------------
# Common Kaniko Build Job (Base)
# --------------------------------------------------------
.kaniko-build-base:
  image: gcr.io/kaniko-project/executor:debug
  stage: package
  before_script:
    # Registry login (GitLab only)
    - mkdir -p /kaniko/.docker
    - echo "{\"auths\":{\"$CI_REGISTRY\":{\"username\":\"$CI_REGISTRY_USER\",\"password\":\"$CI_REGISTRY_PASSWORD\"}}}" > /kaniko/.docker/config.json

  script:
    # -------------------------
    # Resolve Dockerfile path
    # -------------------------
    - |
      if [[ -n "$DOCKER_CUSTOM_BUILD_CONTEXT" ]]; then
        DOCKERFILE_PATH="$DOCKER_CUSTOM_BUILD_CONTEXT/$DOCKERFILE_PATH"
        echo "[INFO] Using custom Dockerfile path: $DOCKERFILE_PATH"
      else
        DOCKERFILE_PATH="$CI_PROJECT_DIR/$DOCKERFILE_PATH"
        echo "[INFO] Using default Dockerfile path: $DOCKERFILE_PATH"
      fi

    # -------------------------
    # Resolve Kaniko destinations (tags)
    # -------------------------
    - |
      KANIKO_DESTINATIONS="$CI_REGISTRY_IMAGE:$DOCKER_TAG"

      # Always add commit SHA
      KANIKO_DESTINATIONS="$KANIKO_DESTINATIONS,$CI_REGISTRY_IMAGE:$CI_COMMIT_SHORT_SHA"

      # If branch = dev → add dev tag
      if [[ "$CI_COMMIT_REF_NAME" == "dev" ]]; then
        KANIKO_DESTINATIONS="$KANIKO_DESTINATIONS,$CI_REGISTRY_IMAGE:dev"
      fi

      # If branch = default branch (main/master) → add prod tag
      if [[ "$CI_COMMIT_REF_NAME" == "$CI_DEFAULT_BRANCH" ]]; then
        KANIKO_DESTINATIONS="$KANIKO_DESTINATIONS,$CI_REGISTRY_IMAGE:prod"
      fi

      # If branch starts with release/ → add version tag
      if [[ "$CI_COMMIT_REF_NAME" =~ ^release/ ]]; then
        RELEASE_TAG=${CI_COMMIT_REF_NAME#"release/"}
        echo "[INFO] Release branch detected → version tag: $RELEASE_TAG"
        KANIKO_DESTINATIONS="$KANIKO_DESTINATIONS,$CI_REGISTRY_IMAGE:$RELEASE_TAG"
        KANIKO_DESTINATIONS="$KANIKO_DESTINATIONS,$CI_REGISTRY_IMAGE:$CI_COMMIT_SHORT_SHA-$RELEASE_TAG"
      fi

      # Add extra tag if provided
      if [[ -n "$EXTRA_DOCKER_TAG" ]]; then
        KANIKO_DESTINATIONS="$KANIKO_DESTINATIONS,$CI_REGISTRY_IMAGE:$EXTRA_DOCKER_TAG"
      fi

      # Add dynamic suffix tag if command provided
      if [[ -n "$DYNAMIC_TAG_SUFFIX_CMD" ]]; then
        DYNAMIC_TAG_SUFFIX=$(eval $DYNAMIC_TAG_SUFFIX_CMD)
        KANIKO_DESTINATIONS="$KANIKO_DESTINATIONS,$CI_REGISTRY_IMAGE:$CI_COMMIT_SHORT_SHA$DYNAMIC_TAG_SUFFIX"
      fi

      echo "[INFO] Final Kaniko Destinations: $KANIKO_DESTINATIONS"

    # -------------------------
    # Run Kaniko executor with cache
    # -------------------------
    - exec /kaniko/executor \
        --context "$CI_PROJECT_DIR" \
        --dockerfile "$DOCKERFILE_PATH" \
        $KANIKO_BUILD_ARGS \
        --cache=true \
        --cache-repo=$CI_REGISTRY_IMAGE/cache \
        --destination $KANIKO_DESTINATIONS

  tags: [$K8S_MEDIUM, $CDP_K8S_TAG]

  artifacts:
    reports:
      dotenv: $GLOBAL_DOTENV
    expire_in: 120 days

# --------------------------------------------------------
# Kaniko Job Variants
# --------------------------------------------------------

# Default build (latest + commit SHA + conditional tags)
kaniko:build-latest:
  extends: .kaniko-build-base
  variables:
    DOCKER_TAG: "latest"
    CS_IMAGE: "$CI_REGISTRY_IMAGE:latest"

# Strict immutable build (only commit SHA + conditional tags)
kaniko:build-sha:
  extends: .kaniko-build-base
  variables:
    DOCKER_TAG: "$CI_COMMIT_SHORT_SHA"
    CS_IMAGE: "$CI_REGISTRY_IMAGE:$CI_COMMIT_SHORT_SHA"

# Jinja2 Dockerfile build
kaniko:build-jinja2:
  extends: .kaniko-build-base
  image: registry.gitlab.com/tmobile/cdp/containers/kaniko:prod
  before_script:
    # Convert Jinja2 template if exists
    - |
      if [[ -f ${DOCKER_CUSTOM_BUILD_CONTEXT}/${DOCKERFILE_PATH}.j2 ]]; then
        echo "[INFO] Converting Jinja2 template..."
        j2 ${DOCKER_CUSTOM_BUILD_CONTEXT}/${DOCKERFILE_PATH}.j2 > ${DOCKER_CUSTOM_BUILD_CONTEXT}/${DOCKERFILE_PATH}
      else
        echo "[INFO] No Jinja2 template found, skipping..."
      fi
    # Registry login
    - mkdir -p /kaniko/.docker
    - echo "{\"auths\":{\"$CI_REGISTRY\":{\"username\":\"$CI_REGISTRY_USER\",\"password\":\"$CI_REGISTRY_PASSWORD\"}}}" > /kaniko/.docker/config.json

# --------------------------------------------------------
# Container Scanning
# --------------------------------------------------------
container_scanning:
  stage: container_scanning
  image: $CS_IMAGE
  tags: [$K8S_MEDIUM, $CDP_K8S_TAG]
