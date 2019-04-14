package moar.awake;

import java.util.UUID;

/**
 * Interface for a data system row.
 * <p>
 * Various inner interfaces here support different approaches to id columns.
 *
 * @author Mark Farnsworth
 */
@SuppressWarnings("javadoc")
public interface WakeableRow {
  interface IdColumn
      extends
      WakeableRow {}

  interface IdColumnAsAutoLong
      extends
      IdColumnAsLong {}

  interface IdColumnAsLong
      extends
      WakeableRow.IdColumn {
    Long getId();
    void setId(Long id);
  }

  interface IdColumnAsString
      extends
      WakeableRow.IdColumn {
    String getId();
    void setId(String id);
  }

  interface IdColumnAsUUID
      extends
      IdColumn {
    UUID getId();
    void setId(UUID id);
  }

  interface WithoutIdColumn
      extends
      WakeableRow {}

}
