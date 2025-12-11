# Docker Setup Guide

## Prerequisites

Before the GitHub Actions workflow can push Docker images, you need to set up Docker Hub authentication.

## Step 1: Create Docker Hub Access Token

1. Go to [Docker Hub Security Settings](https://hub.docker.com/settings/security)
2. Click **"New Access Token"**
3. Give it a name: `GitHub Actions - GamerBell`
4. Select permissions: **Read & Write**
5. Click **"Generate"**
6. **IMPORTANT:** Copy the token immediately - you won't be able to see it again!

## Step 2: Add GitHub Repository Secrets

1. Go to your GitHub repository: `https://github.com/mattlol85/GamerBell`
2. Click **Settings** → **Secrets and variables** → **Actions**
3. Click **"New repository secret"**

### Add DOCKERHUB_USERNAME:
- Name: `DOCKERHUB_USERNAME`
- Secret: `mattlol85`
- Click **"Add secret"**

### Add DOCKERHUB_TOKEN:
- Click **"New repository secret"** again
- Name: `DOCKERHUB_TOKEN`
- Secret: `<paste the token you copied from Docker Hub>`
- Click **"Add secret"**

## Step 3: Verify Setup

After adding the secrets, the next time you push to the main branch:

1. The workflow will build and test your code
2. It will create a new version tag
3. It will create a GitHub release
4. **It will build and push Docker images to Docker Hub**

## Testing the Docker Build

### Test locally before pushing:

```bash
# Build the Docker image
docker build -t mattlol85/bell-api:test .

# Run it locally
docker run -d -p 8080:8080 --name gamerbell-test mattlol85/bell-api:test

# Check if it's running
docker ps

# View logs
docker logs gamerbell-test

# Test the health endpoint
curl http://localhost:8080/actuator/health

# Stop and remove
docker stop gamerbell-test
docker rm gamerbell-test
```

### Test with docker-compose:

```bash
# Start the service
docker-compose up -d

# View logs
docker-compose logs -f

# Stop the service
docker-compose down
```

## Troubleshooting

### If the Docker job fails in GitHub Actions:

1. **Check secrets are set correctly:**
   - Go to Settings → Secrets and variables → Actions
   - Verify both `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN` exist

2. **Verify Docker Hub repository exists:**
   - Go to https://hub.docker.com/r/mattlol85/bell-api
   - Make sure the repository is created (it should already exist)

3. **Check workflow logs:**
   - Go to Actions tab in GitHub
   - Click on the failed workflow
   - Check the "🐳 Build & Push Docker Image" job logs

### If the image won't start:

```bash
# Check container logs
docker logs gamerbell

# Check if the health endpoint is accessible
docker exec gamerbell curl http://localhost:8080/actuator/health
```

## Docker Image Tags

After a successful build, your images will be available at:

- `mattlol85/bell-api:latest` - Always points to the latest release
- `mattlol85/bell-api:0.4.0` - Version number only
- `mattlol85/bell-api:v0.4.0` - Version with 'v' prefix
- `mattlol85/bell-api:sha-abc1234` - Specific git commit

## What Gets Built

The GitHub Actions workflow builds:
- **linux/amd64** - For standard x86_64 servers
- **linux/arm64** - For ARM servers (Raspberry Pi 4, AWS Graviton, etc.)

This means you can run the same image on different architectures!

## Next Steps

1. ✅ Set up Docker Hub access token
2. ✅ Add GitHub secrets
3. ✅ Push to main branch to trigger first build
4. ✅ Verify image appears on Docker Hub
5. ✅ Pull and run on your server: `docker-compose up -d`

