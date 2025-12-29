library(dplyr)
library(ggplot2)

# Join datasets by personId
scatter_twalk <- teleportedWalk_base_NOmods %>%
  select(personId, accessibility) %>%
  rename(access_NOmods = accessibility) %>%
  inner_join(
    teleportedWalk_base_mods %>%
      select(personId, accessibility) %>%
      rename(access_mods = accessibility),
    by = "personId"
  )

# Scatter plot
ggplot(scatter_twalk, aes(x = access_NOmods, y = access_mods)) +
  geom_point(alpha = 0.4, size = 1) +
  geom_abline(slope = 1, intercept = 0, color = "red", linewidth = 1) +
  labs(
    x = "Accessibility (Base – NO mods)",
    y = "Accessibility (Base – mods)",
    title = "teleportedWalk Accessibility: NOmods vs mods"
  ) + coord_equal()
  theme_minimal()

