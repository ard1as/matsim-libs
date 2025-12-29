library(dplyr)
library(ggplot2)

# Join datasets by personId
scatter_car <- car_policy_NOmods %>%
  select(personId, accessibility) %>%
  rename(access_NOmods = accessibility) %>%
  inner_join(
    car_policy_mods %>%
      select(personId, accessibility) %>%
      rename(access_mods = accessibility),
    by = "personId"
  )

# Scatter plot
ggplot(scatter_car, aes(x = access_NOmods, y = access_mods)) +
  geom_point(alpha = 0.4, size = 1) +
  geom_abline(slope = 1, intercept = 0, color = "red", linewidth = 1) +
  labs(
    x = "Accessibility (Policy – NO mods)",
    y = "Accessibility (Policy – mods)",
    title = "Car Accessibility policy: NOmods vs mods"
  ) + coord_equal()
  theme_minimal()

