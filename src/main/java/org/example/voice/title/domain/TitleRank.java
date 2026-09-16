package org.example.voice.title.domain;

public enum TitleRank {
    ABSOLUTE_BEGINNER("왕초보"), BEGINNER("초보"), LOCAL_ANNOUNCER("동네 아나운서"),
    ASPIRING_ANNOUNCER("아나운서 지망생"), ANNOUNCER("아나운서");
    private final String label;
    TitleRank(String label) { this.label = label; }
    public String label() { return label; }
    public TitleRank next() { return ordinal() == values().length - 1 ? null : values()[ordinal()+1]; }
}
