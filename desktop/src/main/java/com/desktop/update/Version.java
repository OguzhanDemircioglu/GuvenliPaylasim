package com.desktop.update;

/** "1.2.3" biçiminde sürüm. Başındaki "v" (git etiketi) yok sayılır. */
public record Version(int major, int minor, int patch) implements Comparable<Version> {

    public static Version parse(String text) {
        String[] parts = text.trim().replaceFirst("^[vV]", "").split("[.-]");
        try {
            return new Version(Integer.parseInt(parts[0]), parts.length > 1 ? Integer.parseInt(parts[1]) : 0,
                    parts.length > 2 ? Integer.parseInt(parts[2]) : 0);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Sürüm okunamadı: " + text, e);
        }
    }

    @Override
    public int compareTo(Version other) {
        int byMajor = Integer.compare(major, other.major);
        if (byMajor != 0) {
            return byMajor;
        }
        int byMinor = Integer.compare(minor, other.minor);
        return byMinor != 0 ? byMinor : Integer.compare(patch, other.patch);
    }

    public boolean isNewerThan(Version other) {
        return compareTo(other) > 0;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
