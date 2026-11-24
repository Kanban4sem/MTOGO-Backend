#!/usr/bin/env bash
# Writes the cycle time of every closed issue on the board to its "Cycle time (h)" field.
# Cycle time: hours from the first move to In Progress until the last close, rounded to one decimal.
# Issues that never reached In Progress get no value. Only changed values are written.
#
# Needs GH_TOKEN with the `project` scope. DRY_RUN=1 prints the values without writing them.
set -euo pipefail

OWNER="${OWNER:-Kanban4sem}"
PROJECT="${PROJECT:-1}"
FIELD="Cycle time (h)"
STATUS="In Progress"

# shellcheck disable=SC2016 # GraphQL variables, not shell variables
QUERY='
query($owner: String!, $project: Int!, $field: String!, $endCursor: String) {
  organization(login: $owner) {
    projectV2(number: $project) {
      id
      field(name: $field) { ... on ProjectV2Field { id } }
      items(first: 50, after: $endCursor) {
        pageInfo { hasNextPage endCursor }
        nodes {
          id
          fieldValueByName(name: $field) { ... on ProjectV2ItemFieldNumberValue { number } }
          content {
            ... on Issue {
              number
              state
              repository { nameWithOwner }
              timelineItems(first: 100, itemTypes: [PROJECT_V2_ITEM_STATUS_CHANGED_EVENT, CLOSED_EVENT]) {
                totalCount
                nodes {
                  __typename
                  ... on ProjectV2ItemStatusChangedEvent { createdAt status project { number } }
                  ... on ClosedEvent { createdAt }
                }
              }
            }
          }
        }
      }
    }
  }
}'

pages=$(gh api graphql --paginate \
  -F owner="$OWNER" -F project="$PROJECT" -F field="$FIELD" -f query="$QUERY")

project_id=$(jq -rs 'first.data.organization.projectV2.id' <<<"$pages")
field_id=$(jq -rs 'first.data.organization.projectV2.field.id // empty' <<<"$pages")
if [[ -z "$field_id" ]]; then
  echo "::error::The board has no Number field named '$FIELD'"
  exit 1
fi

jq -rs '
  .[].data.organization.projectV2.items.nodes[].content
  | select(.timelineItems.totalCount > (.timelineItems.nodes | length))
  | "::warning::\(.repository.nameWithOwner)#\(.number) has more than 100 timeline events; only the first 100 were read"
' <<<"$pages"

# One line per closed issue: item id, issue, new value, current value (last, since it can be empty)
rows=$(jq -rs --argjson project "$PROJECT" --arg status "$STATUS" '
  [.[].data.organization.projectV2.items.nodes[]]
  | .[]
  | select(.content.state == "CLOSED")
  | .content.timelineItems as $timeline
  | ([$timeline.nodes[]
      | select(.__typename == "ProjectV2ItemStatusChangedEvent"
               and .project.number == $project and .status == $status)
      | .createdAt] | min) as $start
  | ([$timeline.nodes[] | select(.__typename == "ClosedEvent") | .createdAt] | max) as $end
  | select($start != null and $end != null and $end > $start)
  | [ .id,
      "\(.content.repository.nameWithOwner)#\(.content.number)",
      ((($end | fromdate) - ($start | fromdate)) / 3600 * 10 | round / 10),
      (.fieldValueByName.number // "")
    ] | @tsv' <<<"$pages")

written=0
unchanged=0
while IFS=$'\t' read -r item issue hours current; do
  [[ -z "$item" ]] && continue
  if [[ -n "$current" ]] && [[ "$(jq -n "$current == $hours")" == "true" ]]; then
    unchanged=$((unchanged + 1))
    continue
  fi
  echo "$issue: ${current:-none} -> $hours h"
  if [[ "${DRY_RUN:-0}" != "1" ]]; then
    gh project item-edit --project-id "$project_id" --id "$item" --field-id "$field_id" --number "$hours" >/dev/null
  fi
  written=$((written + 1))
done <<<"$rows"

echo "Updated: $written, unchanged: $unchanged${DRY_RUN:+ (dry run, nothing written)}"
