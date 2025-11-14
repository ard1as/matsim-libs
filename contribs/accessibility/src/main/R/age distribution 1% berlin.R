library(tidyverse)
library(sf)
library(ggplot2)
library(viridis)
library(classInt)

#load csv
df <- read_csv("person_based_accessibility (walk + age + 1%berlin).csv")

#convert to spatial object
df_sf <- st_as_sf(
  df,
  coords = c("homeX", "homeY"),
  crs = 25832  # UTM 32N, Berlin
)
                  
#transform to WGS84 so ggplot maps work
df_sf <- st_transform(df_sf, 4326)

#basic age distribution
df %>% 
  ggplot(aes(x = age)) +
  geom_histogram(bins = 20, fill = "steelblue", color = "black") +
  geom_text(
    stat = "bin",
    bins = 20,
    aes(label = ..count..),
    vjust = -0.5,           # moves labels slightly above bars
    size = 3                # adjust text size
  ) +
  scale_x_continuous(breaks = seq(min(df$age), max(df$age), by = 5)) +
  theme_minimal() +
  labs(title = "Age Distribution (Berlin 1% Sample)")

#age distribution referring to berlin demographic data
df %>%
  mutate(age_group = cut(
    age,
    breaks = c(-Inf, 15, 25, 45, 65, Inf),
    labels = c("<15", "15–25", "25–45", "45–65", "65+"),
    right = FALSE
  )) %>%
  group_by(age_group) %>%
  summarise(n = n()) %>%
  mutate(pct = n / sum(n) * 100,
         label = paste0(n, " (", sprintf("%.1f%%", pct), ")")) %>%
  ggplot(aes(x = age_group, y = n)) +
  geom_col(fill = "steelblue", color = "black") +
  geom_text(
    aes(label = label),
    vjust = -0.5,
    size = 3
  ) +
  theme_minimal() +
  labs(
    title = "Age Distribution (Berlin 1% Sample)",
    x = "Age Group",
    y = "Count"
  )


ggplot(df_sf) +
  geom_sf(aes(color = age), alpha = 0.6) +
  scale_color_viridis(option = "plasma") +
  theme_minimal() +
  labs(title = "Spatial Distribution of Age in Berlin",
       color = "Age")

# create grid over bounding box
grid <- st_make_grid(
  df_sf,
  cellsize = 250,
  square = TRUE
) %>% st_sf()

# join persons to grid cells
grid_age <- st_join(grid, df_sf) %>%
  group_by(geometry) %>%
  summarise(mean_age = mean(age, na.rm = TRUE),
            count = n())

ggplot(grid_age) +
  geom_sf(aes(fill = mean_age), color = NA) +
  scale_fill_viridis() +
  theme_minimal() +
  labs(title = "Mean Age per 250m Grid Cell in Berlin")
