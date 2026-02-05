# Multimodal person-based accessibility
# teleportedWalk, car (base+policy), pt
# CRS: EPSG: 25382
suppressPackageStartupMessages({
  library(dplyr)
  library(ggplot2)
  library(sf)
  library(readr)
})

# config
pbAcc_teleportedWalk_base_rawSum <- read_csv("pbAcc_teleportedWalk_base_rawSum.csv")
pbAcc_car_base_rawSum <- read_csv("pbAcc_car_base_rawSum.csv")
pbAcc_car_policy_rawSum <- read_csv("pbAcc_car_policy_rawSum.csv")
pbAcc_pt_base_rawSum <- read_csv("pbAcc_pt_base_rawSum.csv")

berlin_shapefiles <- "Berlin shapefiles/Berlin_Bezirke.shp"
bezirke_sf <- st_read(berlin_shapefiles, quiet = TRUE)

output <- "output"
figures <- file.path(output, "figures")
tables <- file.path(output, "tables")
data <- file.path(output, "data")
dir.create(figures, recursive = TRUE, showWarnings = FALSE)
dir.create(tables, recursive = TRUE, showWarnings = FALSE)
dir.create(data, recursive = TRUE, showWarnings = FALSE)

tol <- 1e-6

# df validation + cleaning
clean_df <- function(df){
  df %>% 
    mutate(
      personId = as.character(personId),
      age = as.numeric(age),
      sex = factor(sex, levels = c("f", "m")),
      economic_status = factor(
        economic_status,
        levels = c("very_low", "low", "medium", "high", "very_high"),
        ordered = TRUE
      ),
      income = as.numeric(income),
      carAvail = factor(carAvail, levels = c("always", "never")),
      restricted_mobility = as.logical(restricted_mobility),
      accessibility = as.numeric(accessibility),
      brainExpBeta = as.numeric(brainExpBeta),
      
      age_group = case_when(
        age < 15 ~ "<15",
        age <= 29 ~ "15–29",
        age <= 44 ~ "30–44",
        age <= 59 ~ "45–59",
        TRUE ~ "60+"
      ),
      age_group = factor(
        age_group,
        levels = c("<15", "15–29", "30–44", "45–59", "60+"),
        ordered = TRUE
      ),
      
      # exact income bins (10 values)
      income_bin = case_when(
        income == 499  ~ "499",
        income == 500  ~ "500",
        income == 900  ~ "900",
        income == 1500 ~ "1500",
        income == 2000 ~ "2000",
        income == 2600 ~ "2600",
        income == 3000 ~ "3000",
        income == 3600 ~ "3600",
        income == 4600 ~ "4600",
        income == 5600 ~ "5600",
        TRUE ~ NA_character_
      ),
      income_bin = factor(
        income_bin,
        levels = c("499","500","900","1500","2000","2600","3000","3600","4600","5600"),
        ordered = TRUE
      ),
      
      restricted_mobility = factor(
        restricted_mobility,
        levels = c(FALSE, TRUE),
        labels = c("No restriction", "Restricted mobility")
      )
    )
}

car_base_df <- clean_df(pbAcc_car_base_rawSum)
car_policy_df <- clean_df(pbAcc_car_policy_rawSum)
twalk_base_df <- clean_df(pbAcc_teleportedWalk_base_rawSum) %>% rename(twalk_expSum = accessibility)
pt_base_df <- clean_df(pbAcc_pt_base_rawSum)

# df merge
car_base_expSum <- car_base_df %>%
  select(personId, car_base_expSum = accessibility)
car_policy_expSum <- car_policy_df %>%
  select(personId, car_policy_expSum = accessibility)
pt_expSum <- pt_base_df %>%
  select(personId, pt_expSum = accessibility)

df_merged <- twalk_base_df %>%
  left_join(car_base_expSum,   by = "personId") %>%
  left_join(car_policy_expSum, by = "personId") %>%
  left_join(pt_expSum,         by = "personId") %>% 
  relocate(
    car_base_expSum,
    car_policy_expSum,
    pt_expSum,
    .after = twalk_expSum
  )

# spatial join
if (is.na(st_crs(bezirke_sf))) st_crs(bezirke_sf) <- 25832
if (st_crs(bezirke_sf)$epsg != 25832) {
  bezirke_sf <- st_transform(bezirke_sf, 25832)
}

bez_col <- setdiff(names(bezirke_sf), attr(bezirke_sf, "sf_column"))[1]

join_bezirk <- function(df) {
  st_as_sf(df, coords = c("homeX", "homeY"), crs = 25832, remove = FALSE) %>%
    st_join(bezirke_sf[, bez_col, drop = FALSE]) %>%
    st_drop_geometry() %>%
    rename(bezirk = all_of(bez_col))
}

df_bezirk   <- join_bezirk(df_merged)

# beta value
beta <- df_bezirk$brainExpBeta[1]

# multimodal logsum
df_mm <- df_bezirk %>% 
  mutate(
    # car eligibility (if no car access, exclude car expSum)
    car_base_expSum_adj   = if_else(carAvail == "never" | is.na(carAvail), 0, car_base_expSum),
    car_policy_expSum_adj = if_else(carAvail == "never" | is.na(carAvail), 0, car_policy_expSum),
    # multimodal expSum (across all modes)
    mm_expSum_base = twalk_expSum + pt_expSum + car_base_expSum_adj,
    mm_expSum_policy = twalk_expSum + pt_expSum + car_policy_expSum_adj,
    # multimodal logsum accessibility (utils)
    mm_logSum_base = (1/beta)*log(mm_expSum_base),
    mm_logSum_policy = (1/beta)*log(mm_expSum_policy),
    mm_delta = mm_logSum_policy - mm_logSum_base
  )

summary(df_mm$mm_expSum_base)
summary(df_mm$mm_expSum_policy)
summary(df_mm$mm_delta)

df_mm <- df_mm %>%
  mutate(
    changed = abs(mm_delta) > tol,
    outcome = case_when(
      mm_delta >  tol ~ "Improved",
      mm_delta < -tol ~ "Worsened",
      TRUE ~ "No change"
    )
  )

# group summary (income, age, bezirk)
summarise_mm <- function(df, group_var) {
  df %>%
    group_by(.data[[group_var]]) %>%
    summarise(
      n = n(),
      mean_delta = mean(mm_delta, na.rm = TRUE),
      median_delta = median(mm_delta, na.rm = TRUE),
      p25 = quantile(mm_delta, 0.25, na.rm = TRUE),
      p75 = quantile(mm_delta, 0.75, na.rm = TRUE),
      share_improved = mean(mm_delta > tol, na.rm = TRUE),
      share_worsened = mean(mm_delta < -tol, na.rm = TRUE),
      .groups = "drop"
    )
}

mm_by_income <- summarise_mm(df_mm, "income_bin")
mm_by_age    <- summarise_mm(df_mm, "age_group")
mm_by_bezirk <- summarise_mm(df_mm, "bezirk")

write_csv(mm_by_income, file.path(tables, "mm_delta_by_income_bin.csv"))
write_csv(mm_by_age,    file.path(tables, "mm_delta_by_age_group.csv"))
write_csv(mm_by_bezirk, file.path(tables, "mm_delta_by_bezirk.csv"))

# plots
p_mm_income <- ggplot(df_mm, aes(income_bin, mm_delta)) +
  geom_boxplot(outlier_alpha = 0.3) +
  labs(
    title = "Δ Multi-modal Accessibility (Policy − Base) by Income",
    x = "Income",
    y = "Δ Multi-modal logsum accessibility"
  ) +
  theme_minimal()

ggsave(file.path(figures, "mm_box_delta_by_income.png"), p_mm_income, dpi = 300)

p_mm_age <- ggplot(df_mm, aes(age_group, mm_delta)) +
  geom_boxplot(outlier_alpha = 0.3) +
  labs(
    title = "Δ Multi-modal Accessibility (Policy − Base) by Age Group",
    x = "Age group",
    y = "Δ Multi-modal logsum accessibility"
  ) +
  theme_minimal()

ggsave(file.path(figures, "mm_box_delta_by_age_group.png"), p_mm_age, dpi = 300)

# winners/losers share by income
p_outcome_income <- ggplot(df_mm, aes(income_bin, fill = outcome)) +
  geom_bar(position = "fill") +
  labs(
    title = "Share of population Improved/Worsened (Multi-modal)",
    x = "Income",
    y = "Share"
  ) +
  theme_minimal()

ggsave(file.path(figures, "mm_share_outcome_by_income.png"), p_outcome_income, dpi = 300)
