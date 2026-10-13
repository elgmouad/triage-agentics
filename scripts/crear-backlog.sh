#!/usr/bin/env bash
# ============================================================================
#  TriageAI — Creación del backlog en GitHub (Hito 1)
#
#  Crea, de forma idempotente (se puede relanzar sin duplicar nada):
#    1. las etiquetas (user-story, mvp, area:*)
#    2. los milestones (Hito 1 … Hito 5)
#    3. el tablero GitHub Projects, vinculado al repositorio
#    4. las historias de usuario de docs/backlog/ como issues,
#       añadidas al tablero
#
#  Requisitos : GitHub CLI (gh) autenticado con el permiso "project":
#                 gh auth login
#                 gh auth refresh -s project
#  Uso        : desde la raíz del repositorio →  bash scripts/crear-backlog.sh
# ============================================================================
set -euo pipefail

BACKLOG_DIR="docs/backlog"
PROJECT_TITLE="TriageAI — Backlog"

command -v gh >/dev/null || { echo "ERROR: GitHub CLI (gh) no está instalado."; exit 1; }
[ -f "$BACKLOG_DIR/backlog.tsv" ] || { echo "ERROR: ejecuta el script desde la raíz del repositorio."; exit 1; }

REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner)"
OWNER="${REPO%%/*}"
echo "Repositorio: $REPO"

# ---------------------------------------------------------------- 1. Etiquetas
echo; echo "1/4 · Etiquetas"
crear_etiqueta() {
  gh label create "$1" --repo "$REPO" --color "$2" --description "$3" --force >/dev/null
  echo "  ✓ $1"
}
crear_etiqueta "user-story"           "1D76DB" "Historia de usuario"
crear_etiqueta "mvp"                  "D93F0B" "Producto mínimo viable"
crear_etiqueta "area:tickets"         "0284C7" "servicio-tickets"
crear_etiqueta "area:agente-ia"       "7C3AED" "servicio-agente-triaje"
crear_etiqueta "area:infraestructura" "475569" "Contenedores, IaC, CI/CD, observabilidad"
crear_etiqueta "area:documentacion"   "0E8A16" "Documentación y gestión del proyecto"

# --------------------------------------------------------------- 2. Milestones
echo; echo "2/4 · Milestones"
MILESTONES_EXISTENTES="$(gh api "repos/$REPO/milestones?state=all&per_page=100" --jq '.[].title')"
crear_milestone() {
  if grep -qxF "$1" <<<"$MILESTONES_EXISTENTES"; then
    echo "  = $1 (ya existe)"
  else
    gh api "repos/$REPO/milestones" -f title="$1" -f description="$2" >/dev/null
    echo "  ✓ $1"
  fi
}
crear_milestone "Hito 1" "Definición del proyecto, modelo de negocio y arquitectura de referencia"
crear_milestone "Hito 2" "Empaquetado Cloud Native, entorno local e integración continua (CI)"
crear_milestone "Hito 3" "Aprovisionamiento con IaC y verificación del Reconciler Pattern"
crear_milestone "Hito 4" "Despliegue continuo (CD), reconciliación automatizada y seguridad"
crear_milestone "Hito 5" "Observabilidad, pruebas de carga y release final"

# ------------------------------------------------------------ 3. GitHub Project
echo; echo "3/4 · Tablero GitHub Projects"
PROJECT_NUM="$(gh project list --owner "$OWNER" --limit 100 --format json \
  --jq ".projects[] | select(.title==\"$PROJECT_TITLE\") | .number" | head -n1)"
if [ -z "$PROJECT_NUM" ]; then
  PROJECT_NUM="$(gh project create --owner "$OWNER" --title "$PROJECT_TITLE" --format json --jq .number)"
  echo "  ✓ creado: «$PROJECT_TITLE» (#$PROJECT_NUM)"
else
  echo "  = ya existe: «$PROJECT_TITLE» (#$PROJECT_NUM)"
fi
gh project link "$PROJECT_NUM" --owner "$OWNER" --repo "$REPO" >/dev/null 2>&1 || true

# -------------------------------------------------- 4. Historias de usuario
echo; echo "4/4 · Historias de usuario"
ISSUES_EXISTENTES="$(gh issue list --repo "$REPO" --state all --limit 500 \
  --json title,url --jq '.[] | "\(.title)\t\(.url)"')"

while IFS='|' read -r archivo titulo etiquetas milestone; do
  milestone="${milestone%$'\r'}"
  [[ -z "${archivo// }" || "$archivo" == \#* ]] && continue

  url="$(awk -F'\t' -v t="$titulo" '$1==t {print $2; exit}' <<<"$ISSUES_EXISTENTES")"
  if [ -z "$url" ]; then
    url="$(gh issue create --repo "$REPO" --title "$titulo" \
      --body-file "$BACKLOG_DIR/$archivo" --label "$etiquetas" --milestone "$milestone")"
    echo "  ✓ $titulo  [$milestone]"
  else
    echo "  = $titulo (ya existe)"
  fi
  gh project item-add "$PROJECT_NUM" --owner "$OWNER" --url "$url" >/dev/null
done < "$BACKLOG_DIR/backlog.tsv"

echo
echo "Listo."
echo "  Issues : https://github.com/$REPO/issues"
echo "  Tablero: https://github.com/users/$OWNER/projects/$PROJECT_NUM"
