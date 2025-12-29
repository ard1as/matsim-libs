library(dplyr)

comparison <- pt_base_NOmods %>%
  select(personId, accessibility) %>%
  rename(access_base = accessibility) %>%
  inner_join(
    pt_idk_default %>%
      select(personId, accessibility) %>%
      rename(access_idk = accessibility),
    by = "personId"
  ) %>%
  mutate(
    difference = access_idk - access_base,
    equal = abs(difference) < 1e-6
  )

# Quick check
table(comparison$equal)

# Show only mismatches
comparison %>%
  filter(!equal)
