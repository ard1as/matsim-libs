library(readr)
library(dplyr)
library(ggplot2)
library(tidyr)

#column names in CSVs
age_col   <- "age"
score_col <- "accessibility"

#load data and rename acc score column name
no_mod <- read_csv("person_based_accessibility (walk + nomods).csv", show_col_types = FALSE) %>%
  rename(age = all_of(age_col),
         acc_default = all_of(score_col)) %>%
  select(personId, age, acc_default)

age_mod <- read_csv("person_based_accessibility (walk + age).csv", show_col_types = FALSE) %>%
  rename(age = all_of(age_col),
         acc_age = all_of(score_col)) %>%
  select(personId, age, acc_age)


age_mod %>% view()

#merge df
df <- no_mod %>%
  inner_join(age_mod, by = c("personId", "age")) %>%
  pivot_longer(cols = c(acc_default, acc_age),
               names_to = "source",
               values_to = "score") %>%
  mutate(source = recode(source,
                         acc_default = "Person-based w/o age modifier",
                         acc_age     = "Person-based w/ age modifier"))

#age grouping
df <- df %>%
  mutate(age_group = case_when(
    age <= 14 ~ "0–14",
    age <= 29 ~ "15–29",
    age <= 59 ~ "30–59",
    TRUE      ~ "60+"
  )) %>%
  mutate(
    age_group = factor(age_group, levels = c("0–14", "15–29", "30–59", "60+")),
    source    = factor(source, levels = c("Person-based w/o age modifier",
                                          "Person-based w/ age modifier"))
  )

#Summarise df to one bar per age group per panel
summary_df <- df %>%
  group_by(source, age_group) %>%
  summarise(mean_acc = mean(score, na.rm = TRUE),
            n = n(), .groups = "drop")

#plot
p <- ggplot(summary_df, aes(x = age_group, y = mean_acc)) +
  geom_col(width = 0.7, fill = "grey60") +
  geom_text(aes(label = round(mean_acc, 2)),
            vjust = ifelse(summary_df$mean_acc < 0, 1.2, -0.5),
            size = 4) +
  facet_wrap(~ source, nrow = 1) +
  labs(x = "age group", y = "mean accessibility") +
  theme_minimal(base_size = 13) +
  theme(
    panel.grid.minor = element_blank(),
    strip.text = element_text(face = "bold")
  )

print(p)



age_mod_sf <- read_csv("person_based_accessibility (walk + age).csv") %>%
  st_as_sf(coords = c("homeX", "homeY"), crs = 25832)

tm_shape(grid_sf) +
  tm_symbols(col = "teleportedWalk_accessibility", palette = viridis(10), breaks = seq(-40,0,5), shape = 22, size = 1) + 
  tm_shape(age_mod_sf) + 
  tm_dots(col = "accessibility", palette = viridis(10), breaks = seq(-40,0,5), size = .5)





