library(dplyr)
library(ggplot2)

# Join datasets by personId
scatter_df <- car_base_NOmods %>%
  select(personId, accessibility) %>%
  rename(access_NOmods = accessibility) %>%
  inner_join(
    car_base_mods %>%
      select(personId, accessibility) %>%
      rename(access_mods = accessibility),
    by = "personId"
  )

# Scatter plot
ggplot(scatter_df, aes(x = access_NOmods, y = access_mods)) +
  geom_point(alpha = 0.4, size = 1) +
  geom_abline(slope = 1, intercept = 0, color = "red", linewidth = 1) +
  labs(
    x = "Accessibility (Base – NO mods)",
    y = "Accessibility (Base – mods)",
    title = "Car Accessibility base: NOmods vs mods"
  ) + coord_equal()
theme_minimal()

