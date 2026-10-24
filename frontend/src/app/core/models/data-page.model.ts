/**
 * Generic paginated response, mirroring the backend's `DataPage<T>` record.
 *
 * Only `content`, `page`, `size`, and `totalElements` come from the wire.
 * The derived fields are recomputed on the backend, but mirrored here so
 * consumers don't have to recompute them.
 */
export interface DataPage<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}
