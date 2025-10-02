# --------------------------------------------------------
# Includes shared templates for secret scanning & container scanning
# --------------------------------------------------------
include:
  - project: 'tmobile/templates'
    file: '/gitlab-ci/.sdp.container-secret-scan.gitlab-ci.yml'
  - project: 'tmobile/templates'
    file: '/gitlab-ci/.sdp/container-scanning.latest.gitlab-ci.yml'

# --------------------------------------------------------
# Global variables
# --------------------------------------------------------
variables:
  DOCKER_CUSTOM_BUILD_CONTEXT: ""   # Optional custom build context, default empty
  DOCKERFILE_PATH: 'Dockerfile'     # Default Dockerfile name
  CS_IMAGE: "$CI_REGISTRY_IMAGE:$CI_COMMIT_SHORT_SHA$DEPLOY_AS"  # Image name (commit hash + deploy suffix)

# --------------------------------------------------------
# Shared script: Builds docker image using Kaniko
# --------------------------------------------------------
.kaniko_package_script:
  script:
    # Save current UTC build date
    - export BUILD_DATE=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    - echo "[INFO] DOCKER_CUSTOM_BUILD_CONTEXT: $DOCKER_CUSTOM_BUILD_CONTEXT"

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

    - echo "[INFO] Final DOCKERFILE_PATH: $DOCKERFILE_PATH"

    # -------------------------
    # Resolve build args
    # -------------------------
    - |
      if [[ -n "$DOCKER_BUILD_ARGS" ]]; then
        echo "[INFO] Using DOCKER_BUILD_ARGS for KANIKO_BUILD_ARGS"
        KANIKO_BUILD_ARGS=$DOCKER_BUILD_ARGS
      fi

    - echo "[INFO] KANIKO_BUILD_ARGS: $KANIKO_BUILD_ARGS"

    # -------------------------
    # Registry login
    # -------------------------
    - |
      if [[ -n "$CI_REGISTRY_USER" ]] || [[ -n "$HARBOR_CI_REGISTRY_USER" ]]; then
        echo "[INFO] Logging in to GitLab Container Registry with CI credentials..."
        mkdir -p /kaniko/.docker
        if [[ -n "$CI_REGISTRY_USER" ]]; then
          echo "{\"auths\":{\"$CI_REGISTRY\":{\"username\":\"$CI_REGISTRY_USER\",\"password\":\"$CI_REGISTRY_PASSWORD\"}}}" > /kaniko/.docker/config.json
        else
          echo "{\"auths\":{\"$HARBOR_CI_REGISTRY\":{\"username\":\"$HARBOR_CI_REGISTRY_USER\",\"password\":\"$HARBOR_CI_REGISTRY_PASSWORD\"}}}" > /kaniko/.docker/config.json
        fi
      fi

    # -------------------------
    # Define Kaniko image destinations (tags)
    # -------------------------
    - echo "[INFO] Setting Kaniko destinations"
    - export KANIKO_DESTINATIONS="$CI_REGISTRY_IMAGE:$DOCKER_TAG_LATEST"

    # Tag with branch name if branch == dev
    - |
      if [[ "$CI_COMMIT_REF_NAME" == "dev" ]]; then
        export KANIKO_DESTINATIONS="$KANIKO_DESTINATIONS,$CI_REGISTRY_IMAGE:$CI_COMMIT_REF_NAME"
      fi

    # Tag with prod if branch == default branch (main/master)
    - |
      if [[ "$CI_COMMIT_REF_NAME" == "$CI_DEFAULT_BRANCH" ]]; then
        export KANIKO_DESTINATIONS="$KANIKO_DESTINATIONS,$CI_REGISTRY_IMAGE:prod"
      fi

    # Add extra tag if provided
    - |
      if [[ -n "$EXTRA_DOCKER_TAG" ]]; then
        export KANIKO_DESTINATIONS="$KANIKO_DESTINATIONS,$CI_REGISTRY_IMAGE:$EXTRA_DOCKER_TAG"
      fi

    # Add dynamic suffix tag if command provided
    - |
      if [[ -n "$DYNAMIC_TAG_SUFFIX_CMD" ]]; then
        DYNAMIC_TAG_SUFFIX=$(eval $DYNAMIC_TAG_SUFFIX_CMD)
        export KANIKO_DESTINATIONS="$KANIKO_DESTINATIONS,$CI_REGISTRY_IMAGE:$CI_COMMIT_SHORT_SHA$DYNAMIC_TAG_SUFFIX"
      fi

    - echo "[INFO] Final Kaniko Destinations: $KANIKO_DESTINATIONS"

    # -------------------------
    # Run Kaniko executor
    # -------------------------
    - exec /kaniko/executor \
        --context "$CI_PROJECT_DIR" \
        --dockerfile "$DOCKERFILE_PATH" \
        $KANIKO_BUILD_ARGS \
        --destination $KANIKO_DESTINATIONS


# --------------------------------------------------------
# Script for Jinja2-based Dockerfile builds
# --------------------------------------------------------
.kaniko_package_script_j2:
  script:
    - |
      if [[ ! -f ${DOCKER_CUSTOM_BUILD_CONTEXT}/${DOCKERFILE_PATH}.j2 ]]; then
        echo "[INFO] Jinja template ${DOCKERFILE_PATH}.j2 not found"
      else
        echo "[INFO] Converting Jinja2 template..."
        if [[ -f ${DOCKER_CUSTOM_BUILD_CONTEXT}/${DOCKERFILE_PATH} ]]; then
          echo "[ERROR] File ${DOCKERFILE_PATH} already exists! Exiting..."
          exit 1
        fi
        j2 ${DOCKER_CUSTOM_BUILD_CONTEXT}/${DOCKERFILE_PATH}.j2 > ${DOCKER_CUSTOM_BUILD_CONTEXT}/${DOCKERFILE_PATH}
      fi


# --------------------------------------------------------
# Kaniko Job: Standard Docker Build
# --------------------------------------------------------
.kaniko-package:
  image: gcr.io/kaniko-project/executor:debug   # Kaniko debug image
  stage: package
  script: *kaniko_package_script                # Reuse script defined above
  tags:
    - $K8S_MEDIUM
    - $CDP_K8S_TAG
  variables:
    DOCKER_TAG: "latest"                        # Default tag: latest
    CS_IMAGE: "$CI_REGISTRY_IMAGE:latest"
  artifacts:
    reports:
      dotenv: $GLOBAL_DOTENV                    # Save env variables for next jobs
    expire_in: 120 days


# --------------------------------------------------------
# Kaniko Job: Without 'latest' tag (only commit hash)
# --------------------------------------------------------
.kaniko-package-without-latest:
  extends: .kaniko-package
  variables:
    DOCKER_TAG: "$CI_COMMIT_SHORT_SHA"
    CS_IMAGE: "$CI_REGISTRY_IMAGE:$CI_COMMIT_SHORT_SHA"


# --------------------------------------------------------
# Kaniko Job: Jinja2 Template Dockerfile
# --------------------------------------------------------
.kaniko-package-j2:
  image: registry.gitlab.com/tmobile/cdp/containers/kaniko:prod
  stage: package
  script: *kaniko_package_script_j2
  tags:
    - $K8S_MEDIUM
    - $CDP_K8S_TAG
  variables:
    DOCKER_TAG: "$CI_COMMIT_SHORT_SHA"
    CS_IMAGE: "$CI_REGISTRY_IMAGE:$CI_COMMIT_SHORT_SHA"


# --------------------------------------------------------
# Container Scanning Job
# --------------------------------------------------------
container_scanning:
  stage: container_scanning
  image: $CS_IMAGE
  tags:
    - $K8S_MEDIUM
    - $CDP_K8S_TAG
