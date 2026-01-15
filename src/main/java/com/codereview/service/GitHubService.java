package com.codereview.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Service;

import com.codereview.config.CodeReviewProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubService {

    private final CodeReviewProperties properties;

    /**
     * Clones a GitHub repository to a local temporary directory.
     *
     * @param repositoryUrl The URL of the repository to clone
     * @param branch The branch to checkout
     * @return The path to the cloned repository
     * @throws GitAPIException If cloning fails
     */
    public Path cloneRepository(String repositoryUrl, String branch) throws GitAPIException {
        String cloneDir = properties.getGithub().getCloneDirectory();
        if (cloneDir == null || cloneDir.isEmpty()) {
            cloneDir = System.getProperty("java.io.tmpdir") + "/code-review-repos";
        }

        Path repoPath = Path.of(cloneDir, UUID.randomUUID().toString());

        log.info("Cloning repository {} (branch: {}) to {}", repositoryUrl, branch, repoPath);

        Git.cloneRepository()
                .setURI(repositoryUrl)
                .setDirectory(repoPath.toFile())
                .setBranch(branch)
                .setCredentialsProvider(getCredentialsProvider())
                .call()
                .close();

        log.info("Repository cloned successfully to {}", repoPath);
        return repoPath;
    }

    /**
     * Clones a repository and checks out a specific commit.
     *
     * @param repositoryUrl The URL of the repository to clone
     * @param branch The branch name
     * @param commitSha The specific commit SHA to checkout
     * @return The path to the cloned repository
     * @throws GitAPIException If cloning or checkout fails
     */
    public Path cloneRepositoryAtCommit(String repositoryUrl, String branch, String commitSha)
            throws GitAPIException {
        Path repoPath = cloneRepository(repositoryUrl, branch);

        if (commitSha != null && !commitSha.isEmpty()) {
            try (Git git = Git.open(repoPath.toFile())) {
                log.info("Checking out commit {}", commitSha);
                git.checkout()
                        .setName(commitSha)
                        .call();
            } catch (IOException e) {
                log.error("Failed to open repository for checkout", e);
                throw new RuntimeException("Failed to checkout commit: " + commitSha, e);
            }
        }

        return repoPath;
    }

    /**
     * Gets the current commit SHA of the repository.
     *
     * @param repoPath Path to the repository
     * @return The current commit SHA
     */
    public String getCurrentCommitSha(Path repoPath) {
        try (Git git = Git.open(repoPath.toFile())) {
            return git.getRepository().resolve("HEAD").getName();
        } catch (IOException e) {
            log.error("Failed to get current commit SHA", e);
            return null;
        }
    }

    /**
     * Cleans up a cloned repository.
     *
     * @param repoPath The path to the repository to clean up
     */
    public void cleanupRepository(Path repoPath) {
        if (repoPath != null && Files.exists(repoPath)) {
            try (Stream<Path> walk = Files.walk(repoPath)) {
                walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                log.info("Cleaned up repository at {}", repoPath);
            } catch (IOException e) {
                log.warn("Failed to clean up repository at {}: {}", repoPath, e.getMessage());
            }
        }
    }

    /**
     * Validates a GitHub repository URL.
     *
     * @param repositoryUrl The URL to validate
     * @return true if the URL is valid
     */
    public boolean isValidRepositoryUrl(String repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.isEmpty()) {
            return false;
        }

        return repositoryUrl.matches("https?://github\\.com/[\\w.-]+/[\\w.-]+(\\.git)?/?")
                || repositoryUrl.matches("git@github\\.com:[\\w.-]+/[\\w.-]+(\\.git)?");
    }

    /**
     * Extracts repository name from URL.
     *
     * @param repositoryUrl The repository URL
     * @return The repository name
     */
    public String extractRepoName(String repositoryUrl) {
        String cleaned = repositoryUrl.replaceAll("\\.git$", "").replaceAll("/$", "");
        int lastSlash = cleaned.lastIndexOf('/');
        return lastSlash >= 0 ? cleaned.substring(lastSlash + 1) : cleaned;
    }

    private UsernamePasswordCredentialsProvider getCredentialsProvider() {
        String token = properties.getGithub().getToken();
        if (token != null && !token.isEmpty()) {
            return new UsernamePasswordCredentialsProvider(token, "");
        }
        return null;
    }
}
