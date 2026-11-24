# Diccionario de datos — base de datos v1

Generado a partir del esquema exportado `app/schemas/com.geodensidad.app.data.AppDatabase/1.json`. Claves primarias UUID (texto). Fechas de sistema en milisegundos epoch; fechas de negocio en ISO-8601. Números de entrada como decimal canónico en texto. Humedades como fracción.

Reglas: claves foráneas con RESTRICT (no se borran registros referenciados; se archivan). Columnas `cache_*` de `samples` son derivadas de la revisión vigente. `data_json` de `sample_revisions` contiene el formulario completo (`SampleForm`) y `calc_json` la instantánea de cálculo (`LegacyResult`).

## `projects`

| Columna | Tipo | Obligatoria |
|---|---|---|
| `id` | TEXT | sí |
| `code` | TEXT | sí |
| `name` | TEXT | sí |
| `client` | TEXT | sí |
| `contractor` | TEXT | sí |
| `location` | TEXT | sí |
| `description` | TEXT | sí |
| `responsibles` | TEXT | sí |
| `status` | TEXT | sí |
| `is_demo` | INTEGER | sí |
| `created_at` | INTEGER | sí |
| `updated_at` | INTEGER | sí |

Índices: `code`

## `activities`

| Columna | Tipo | Obligatoria |
|---|---|---|
| `id` | TEXT | sí |
| `project_id` | TEXT | sí |
| `name` | TEXT | sí |
| `description` | TEXT | sí |
| `test_type` | TEXT | sí |
| `default_material_id` | TEXT | no |
| `default_calibration_id` | TEXT | no |
| `default_lab_ref_id` | TEXT | no |
| `default_criterion_id` | TEXT | no |
| `default_bulk` | TEXT | no |
| `sample_prefix` | TEXT | sí |
| `status` | TEXT | sí |
| `created_at` | INTEGER | sí |
| `updated_at` | INTEGER | sí |
| `last_opened_at` | INTEGER | sí |

Claves foráneas: `project_id` → `projects`

Índices: `project_id`; `status`

## `journeys`

| Columna | Tipo | Obligatoria |
|---|---|---|
| `id` | TEXT | sí |
| `activity_id` | TEXT | sí |
| `work_date` | TEXT | sí |
| `responsible` | TEXT | sí |
| `work_front` | TEXT | sí |
| `observations` | TEXT | sí |
| `weather` | TEXT | sí |
| `status` | TEXT | sí |
| `custom_json` | TEXT | sí |
| `created_at` | INTEGER | sí |
| `updated_at` | INTEGER | sí |

Claves foráneas: `activity_id` → `activities`

Índices: `activity_id`; `work_date`

## `lots`

| Columna | Tipo | Obligatoria |
|---|---|---|
| `id` | TEXT | sí |
| `activity_id` | TEXT | sí |
| `code` | TEXT | sí |
| `description` | TEXT | sí |
| `material_id` | TEXT | no |
| `section` | TEXT | sí |
| `fill_part` | TEXT | sí |
| `lab_ref_id` | TEXT | no |
| `criterion_id` | TEXT | no |
| `review_status` | TEXT | sí |
| `created_at` | INTEGER | sí |
| `updated_at` | INTEGER | sí |

Claves foráneas: `activity_id` → `activities`

Índices: `activity_id`; único `activity_id,code`

## `samples`

| Columna | Tipo | Obligatoria |
|---|---|---|
| `id` | TEXT | sí |
| `activity_id` | TEXT | sí |
| `journey_id` | TEXT | sí |
| `lot_id` | TEXT | no |
| `code` | TEXT | sí |
| `seq` | INTEGER | sí |
| `status` | TEXT | sí |
| `current_revision_id` | TEXT | sí |
| `revision_number` | INTEGER | sí |
| `test_datetime` | TEXT | no |
| `abscisa_text` | TEXT | no |
| `abscisa_m` | REAL | no |
| `side` | TEXT | no |
| `layer` | TEXT | no |
| `material_id` | TEXT | no |
| `lab_ref_id` | TEXT | no |
| `calibration_id` | TEXT | no |
| `cache_p` | REAL | no |
| `cache_s` | REAL | no |
| `cache_l` | REAL | no |
| `cache_r` | REAL | no |
| `cache_q` | REAL | no |
| `cache_blocking` | INTEGER | sí |
| `void_reason` | TEXT | no |
| `lot_excluded` | INTEGER | sí |
| `lot_exclusion_reason` | TEXT | no |
| `created_at` | INTEGER | sí |
| `updated_at` | INTEGER | sí |

Claves foráneas: `activity_id` → `activities`; `journey_id` → `journeys`; `lot_id` → `lots`

Índices: `activity_id`; `journey_id`; `lot_id`; `status`; único `activity_id,code`; `abscisa_m`

## `sample_revisions`

| Columna | Tipo | Obligatoria |
|---|---|---|
| `id` | TEXT | sí |
| `sample_id` | TEXT | sí |
| `number` | INTEGER | sí |
| `status` | TEXT | sí |
| `data_json` | TEXT | sí |
| `calc_json` | TEXT | no |
| `calibration_snapshot` | TEXT | no |
| `lab_ref_snapshot` | TEXT | no |
| `engine_version` | TEXT | sí |
| `reason` | TEXT | no |
| `created_by` | TEXT | sí |
| `created_at` | INTEGER | sí |
| `updated_at` | INTEGER | sí |
| `reviewed_by` | TEXT | no |
| `reviewed_at` | INTEGER | no |
| `approved_by` | TEXT | no |
| `approved_at` | INTEGER | no |
| `locked` | INTEGER | sí |
| `superseded` | INTEGER | sí |

Claves foráneas: `sample_id` → `samples`

Índices: `sample_id`; único `sample_id,number`

## `materials`

| Columna | Tipo | Obligatoria |
|---|---|---|
| `id` | TEXT | sí |
| `code` | TEXT | sí |
| `name` | TEXT | sí |
| `description` | TEXT | sí |
| `archived` | INTEGER | sí |
| `updated_at` | INTEGER | sí |

## `equipment`

| Columna | Tipo | Obligatoria |
|---|---|---|
| `id` | TEXT | sí |
| `code` | TEXT | sí |
| `name` | TEXT | sí |
| `serial` | TEXT | sí |
| `archived` | INTEGER | sí |
| `updated_at` | INTEGER | sí |

## `calibrations`

| Columna | Tipo | Obligatoria |
|---|---|---|
| `id` | TEXT | sí |
| `code` | TEXT | sí |
| `equipment_id` | TEXT | sí |
| `date` | TEXT | sí |
| `valid_until` | TEXT | no |
| `cone_constant_g` | TEXT | sí |
| `sand_density` | TEXT | sí |
| `notes` | TEXT | sí |
| `archived` | INTEGER | sí |
| `updated_at` | INTEGER | sí |

Claves foráneas: `equipment_id` → `equipment`

Índices: `equipment_id`

## `lab_references`

| Columna | Tipo | Obligatoria |
|---|---|---|
| `id` | TEXT | sí |
| `code` | TEXT | sí |
| `material_id` | TEXT | no |
| `test_id` | TEXT | sí |
| `max_dry_density` | TEXT | sí |
| `density_unit` | TEXT | sí |
| `optimum_moisture` | TEXT | no |
| `document_path` | TEXT | no |
| `notes` | TEXT | sí |
| `archived` | INTEGER | sí |
| `updated_at` | INTEGER | sí |

## `people`

| Columna | Tipo | Obligatoria |
|---|---|---|
| `id` | TEXT | sí |
| `name` | TEXT | sí |
| `role` | TEXT | sí |
| `archived` | INTEGER | sí |
| `updated_at` | INTEGER | sí |

## `criteria`

| Columna | Tipo | Obligatoria |
|---|---|---|
| `id` | TEXT | sí |
| `code` | TEXT | sí |
| `version` | TEXT | sí |
| `json` | TEXT | sí |
| `validated` | INTEGER | sí |
| `archived` | INTEGER | sí |
| `updated_at` | INTEGER | sí |

## `template_versions`

| Columna | Tipo | Obligatoria |
|---|---|---|
| `id` | TEXT | sí |
| `version` | TEXT | sí |
| `institution` | TEXT | sí |
| `form_title` | TEXT | sí |
| `doc_code` | TEXT | sí |
| `doc_version` | TEXT | sí |
| `doc_date` | TEXT | sí |
| `logo_path` | TEXT | no |
| `logo_w` | INTEGER | sí |
| `logo_h` | INTEGER | sí |
| `formula_path` | TEXT | no |
| `formula_w` | INTEGER | sí |
| `formula_h` | INTEGER | sí |
| `active` | INTEGER | sí |
| `created_at` | INTEGER | sí |

Índices: único `version`

## `custom_fields`

| Columna | Tipo | Obligatoria |
|---|---|---|
| `id` | TEXT | sí |
| `field_key` | TEXT | sí |
| `entity` | TEXT | sí |
| `label` | TEXT | sí |
| `description` | TEXT | sí |
| `type` | TEXT | sí |
| `unit` | TEXT | sí |
| `required` | INTEGER | sí |
| `options_json` | TEXT | sí |
| `min_value` | TEXT | no |
| `max_value` | TEXT | no |
| `sort_order` | INTEGER | sí |
| `include_pdf` | INTEGER | sí |
| `include_table` | INTEGER | sí |
| `version` | INTEGER | sí |
| `archived` | INTEGER | sí |
| `updated_at` | INTEGER | sí |

Índices: único `field_key`

## `photos`

| Columna | Tipo | Obligatoria |
|---|---|---|
| `id` | TEXT | sí |
| `activity_id` | TEXT | sí |
| `sample_id` | TEXT | no |
| `journey_id` | TEXT | no |
| `origin` | TEXT | sí |
| `original_path` | TEXT | sí |
| `thumb_path` | TEXT | sí |
| `pdf_path` | TEXT | sí |
| `pdf_w` | INTEGER | sí |
| `pdf_h` | INTEGER | sí |
| `sha256` | TEXT | sí |
| `mime` | TEXT | sí |
| `width` | INTEGER | sí |
| `height` | INTEGER | sí |
| `size_bytes` | INTEGER | sí |
| `exif_orientation` | INTEGER | sí |
| `rotation` | INTEGER | sí |
| `times_json` | TEXT | sí |
| `caption_json` | TEXT | sí |
| `include_report` | INTEGER | sí |
| `sort_order` | INTEGER | sí |
| `latitude` | REAL | no |
| `longitude` | REAL | no |
| `archived` | INTEGER | sí |
| `created_at` | INTEGER | sí |
| `updated_at` | INTEGER | sí |

Claves foráneas: `activity_id` → `activities`; `sample_id` → `samples`; `journey_id` → `journeys`

Índices: `sample_id`; `journey_id`; `activity_id`; `sha256`

## `reports`

| Columna | Tipo | Obligatoria |
|---|---|---|
| `id` | TEXT | sí |
| `code` | TEXT | sí |
| `type` | TEXT | sí |
| `activity_id` | TEXT | sí |
| `sample_id` | TEXT | no |
| `revision_id` | TEXT | no |
| `issued_at` | INTEGER | sí |
| `issued_at_text` | TEXT | sí |
| `issued_by` | TEXT | no |
| `draft` | INTEGER | sí |
| `template_version` | TEXT | sí |
| `engine_version` | TEXT | sí |
| `file_path` | TEXT | sí |
| `snapshot_path` | TEXT | sí |
| `sha256` | TEXT | sí |
| `page_count` | INTEGER | sí |
| `scope_text` | TEXT | sí |

Claves foráneas: `activity_id` → `activities`

Índices: `activity_id`; `sample_id`

## `audit_events`

| Columna | Tipo | Obligatoria |
|---|---|---|
| `id` | TEXT | sí |
| `at` | INTEGER | sí |
| `actor` | TEXT | sí |
| `entity_type` | TEXT | sí |
| `entity_id` | TEXT | sí |
| `action` | TEXT | sí |
| `detail` | TEXT | sí |

Índices: `entity_id`; `at`
