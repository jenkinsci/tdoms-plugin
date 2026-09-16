package io.jenkins.plugins.tdoms.model;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single changed source file, split into fileName / relativePath / extension for convenience
 * in pipeline scripts.
 */
public final class ChangedFile implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String fileName;
    private final String relativePath;
    private final String extension;

    public ChangedFile(String relativePath) {
        this.relativePath = relativePath;

        String normalized = relativePath.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        this.fileName = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;

        int lastDot = fileName.lastIndexOf('.');
        this.extension = lastDot > 0 ? fileName.substring(lastDot + 1) : "";
    }

    public String getFileName() {
        return fileName;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getExtension() {
        return extension;
    }

    /** Returned to pipeline scripts as a plain Map so it can be used without sandbox approval. */
    public Map<String, String> toMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("fileName", fileName);
        map.put("relativePath", relativePath);
        map.put("extension", extension);
        return map;
    }
}
