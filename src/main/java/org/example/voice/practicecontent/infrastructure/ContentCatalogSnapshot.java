package org.example.voice.practicecontent.infrastructure;

import java.time.OffsetDateTime;

/** Public data is valid only until the next matching scheduled publication. */
public record ContentCatalogSnapshot<T>(T value, OffsetDateTime nextPublicationAt) {}
