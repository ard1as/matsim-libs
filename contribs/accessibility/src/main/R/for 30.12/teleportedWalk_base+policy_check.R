library(dplyr)

comparison_twalk <- teleportedWalk_base_mods %>%
  select(personId, accessibility) %>%
  rename(access_base = accessibility) %>%
  inner_join(
    teleportedWalk_policy_mods %>%
      select(personId, accessibility) %>%
      rename(access_policy = accessibility),
    by = "personId"
  ) %>%
  mutate(
    difference = access_policy - access_base,
    equal = abs(difference) < 1e-6
  )

# Quick check
table(comparison_twalk$equal)

# Show only mismatches
comparison_twalk %>%
  filter(!equal)
