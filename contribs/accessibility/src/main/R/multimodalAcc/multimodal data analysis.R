# Multimodal person-based accessibility
# teleportedWalk, car (base+policy), pt
# CRS: EPSG: 25382
suppressPackageStartupMessages({
  library(dplyr)
  library(ggplot2)
  library(sf)
  library(readr)
  library(scales)
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
        income %in% c(499,500) ~ "500",
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
        levels = c("500","900","1500","2000","2600","3000","3600","4600","5600"),
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
pt_expSum <- pt_base_df %>%
  select(personId, pt_expSum = accessibility)
car_base_expSum <- car_base_df %>%
  select(personId, car_base_expSum = accessibility)
car_policy_expSum <- car_policy_df %>%
  select(personId, car_policy_expSum = accessibility)

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

join_bezirk <- function(df) {
  st_as_sf(df, coords = c("homeX", "homeY"), crs = 25832, remove = FALSE) %>%
    st_join(bezirke_sf[, "Gemeinde_n", drop = FALSE]) %>%
    st_drop_geometry() %>%
    rename(district_ord = Gemeinde_n)
}

df_bezirk <- join_bezirk(df_merged)

# beta value
beta <- df_bezirk$brainExpBeta[1]

# multimodal logsum
df_mm <- df_bezirk %>% 
  mutate(
    # car eligibility (if no car access, exclude car expSum) TOGGLE!!!
    #car_base_expSum_adj   = if_else(carAvail == "never" | is.na(carAvail), 0, car_base_expSum),
    #car_policy_expSum_adj = if_else(carAvail == "never" | is.na(carAvail), 0, car_policy_expSum),
    # multimodal expSum (across all modes) ADD/REMOVE "_adj"
    mm_expSum_base = twalk_expSum + pt_expSum + car_base_expSum,
    mm_expSum_policy = twalk_expSum + pt_expSum + car_policy_expSum,
    # multimodal logsum accessibility (utils)
    mm_logSum_base = (1/beta)*log(mm_expSum_base),
    mm_logSum_policy = (1/beta)*log(mm_expSum_policy),
    mm_delta = mm_logSum_policy - mm_logSum_base,
    car_share_base = car_base_expSum / mm_expSum_base
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

df_mm <- df_mm %>%
  mutate(
    twalk_logsum = (1/beta) * log(twalk_expSum),
    pt_logSum = (1/beta) * log(pt_expSum),
    # car-only logsum (respecting car availability via *_adj)
    car_logSum_base = (1/beta) * log(car_base_expSum),
    car_logSum_policy = (1/beta) * log(car_policy_expSum),
    car_delta = car_logSum_policy - car_logSum_base
  )

df_mm <- df_mm %>%
  mutate(
    district_ord = factor(
      district_ord,
      levels = c(
        "Mitte",
        "Friedrichshain-Kreuzberg",
        "Pankow",
        "Charlottenburg-Wilmersdorf",
        "Spandau",
        "Steglitz-Zehlendorf",
        "Tempelhof-Schöneberg",
        "Neukölln",
        "Treptow-Köpenick",
        "Marzahn-Hellersdorf",
        "Lichtenberg",
        "Reinickendorf"
      ),
      ordered = TRUE
    )
  )

write_csv(df_mm, file="mmAcc.csv")


# group summary (income, age, bezirk)
summarise_mm <- function(df, group_var) {
  df %>%
    group_by(.data[[group_var]]) %>%
    summarise(
      n = n(),
      
      # shares (computed from row-wise mm_delta)
      share_improved  = mean(mm_delta >  tol,  na.rm = TRUE),
      share_worsened  = mean(mm_delta < -tol,  na.rm = TRUE),
      share_no_change = mean(abs(mm_delta) <= tol, na.rm = TRUE),
      
      # distribution stats (row-wise)
      mean_delta   = mean(mm_delta, na.rm = TRUE),
      median_delta = median(mm_delta, na.rm = TRUE),
      p25          = quantile(mm_delta, 0.25, na.rm = TRUE),
      p75          = quantile(mm_delta, 0.75, na.rm = TRUE),
      
      # requested averages (use *_avg to avoid masking)
      twalk_expSum_avg = mean(twalk_expSum, na.rm = TRUE),
      pt_expSum_avg = mean(pt_expSum, na.rm = TRUE),
      car_base_expSum_avg = mean(car_base_expSum, na.rm = TRUE),
      car_policy_expSum_avg = mean(car_policy_expSum, na.rm = TRUE),
      
      mm_logSum_base_avg = mean(mm_logSum_base, na.rm = TRUE),
      mm_logSum_policy_avg = mean(mm_logSum_policy, na.rm = TRUE),
      mm_delta_avg = mean(mm_delta, na.rm = TRUE),
      
      car_logSum_base_avg =
        if (all(is.na(car_logSum_base))) NA_real_ else mean(car_logSum_base, na.rm = TRUE),
      car_logSum_policy_avg =
        if (all(is.na(car_logSum_policy))) NA_real_ else mean(car_logSum_policy, na.rm = TRUE),
      car_delta_avg =
        if (all(is.na(car_delta))) NA_real_ else mean(car_delta, na.rm = TRUE),
      
      .groups = "drop"
    )
}

mm_by_income <- summarise_mm(df_mm, "income_bin")
mm_by_age    <- summarise_mm(df_mm, "age_group")
mm_by_bezirk <- summarise_mm(df_mm, "district_ord")

write_csv(mm_by_income, file.path(tables, "mm_delta_by_income_bin.csv"))
write_csv(mm_by_age,    file.path(tables, "mm_delta_by_age_group.csv"))
write_csv(mm_by_bezirk, file.path(tables, "mm_delta_by_bezirk.csv"))

# mm_delta by income, age, district plots
p_mm_income <- ggplot(df_mm, aes(income_bin, mm_delta)) +
  geom_boxplot(outlier.alpha = 0.3) +
  labs(
    title = "Δ Multimodal accessibility (policy − base) by income",
    x = "Income",
    y = "Δ Multimodal accessibility"
  ) +
  theme(axis.text = element_text(size = 14),
        axis.title = element_text(size = 14),
        legend.text = element_text(size = 14),
        legend.title = element_text(size = 14))

ggsave(file.path(figures, "mm_box_delta_by_income.png"), p_mm_income, dpi = 300)

p_mm_age <- ggplot(df_mm, aes(age_group, mm_delta)) +
  geom_boxplot(outlier.alpha = 0.3) +
  labs(
    title = "Δ Multimodal accessibility (policy − base) by age group",
    x = "Age group",
    y = "Δ Multimodal accessibility"
  ) +
  theme(axis.text = element_text(size = 14),
        axis.title = element_text(size = 14),
        legend.text = element_text(size = 14),
        legend.title = element_text(size = 14))

ggsave(file.path(figures, "mm_box_delta_by_age_group.png"), p_mm_age, dpi = 300)

p_mm_bezirk <- ggplot(df_mm, aes(x = district_ord,
                                 y = mm_delta)) +
  geom_boxplot(outlier.alpha = 0.3, na.rm = TRUE) +
  labs(
    title = "Δ Multimodal accessibility (policy − base) by district",
    x = "District",
    y = "Δ Multimodal accessibility"
  ) +
  theme(axis.text.x = element_text(angle = 45, hjust = 1))

ggsave(file.path(figures, "mm_box_delta_by_bezirk.png"),
       p_mm_bezirk,
       dpi = 300,
       width = 10,
       height = 6)


# winners/losers share by income
p_outcome_income <- ggplot(df_mm, aes(income_bin, fill = outcome)) +
  geom_bar(position = "fill") +
  labs(
    title = "Share of population Improved/Worsened (Multimodal accesssibility) by income",
    x = "Income",
    y = "Share"
  ) +
  theme()

ggsave(file.path(figures, "mm_share_outcome_by_income.png"), p_outcome_income, dpi = 300)

p_outcome_age <- ggplot(df_mm, aes(age_group, fill = outcome)) +
  geom_bar(position = "fill") +
  labs(
    title = "Share of population Improved/Worsened (Multimodal accessibility) by age group",
    x = "Age group",
    y = "Share"
  ) +
  theme()

ggsave(file.path(figures, "mm_share_outcome_by_age.png"), p_outcome_age, dpi = 300)

# winners/losers share by district
p_outcome_district <- ggplot(df_mm, aes(x = district_ord, fill = outcome)) +
  geom_bar(position = "fill") +
  labs(
    title = "Share of population Improved/Worsened (Multimodal accessibility) by district",
    x = "District",
    y = "Share"
  ) +
  theme(axis.text.x = element_text(angle = 30, hjust = 1))

ggsave(file.path(figures, "mm_share_outcome_by_district.png"), p_outcome_district, dpi = 300, width = 11, height = 6)



p_car_income <- ggplot(df_mm, aes(income_bin, car_delta)) +
  geom_boxplot(outlier.alpha = 0.3, na.rm = TRUE) +
  labs(
    title = "Δ Car-only accessibility (policy − base) by income",
    x = "Income",
    y = "Δ Car-only accessibility"
  ) +
  theme_minimal()

ggsave(file.path(figures, "car_box_delta_by_income.png"),
       p_car_income,
       dpi = 300)

#histogram mm acc (base)
p_mm_hist_base <- ggplot(df_mm, aes(mm_logSum_base)) +
  geom_histogram(bins = 40) +
  labs(
    title = "Histogram of Multi-modal Accessibility (Base)",
    x = "Multi-modal logsum accessibility (base)",
    y = "Count"
  ) +
  theme()

ggsave(file.path(figures, "mm_hist_base.png"), p_mm_hist_base, dpi = 300)

p_mm_hist_policy <- ggplot(df_mm, aes(mm_logSum_policy)) +
  geom_histogram(bins = 40) +
  labs(
    title = "Histogram of Multi-modal Accessibility (Policy)",
    x = "Multi-modal logsum accessibility (Policy)",
    y = "Count"
  ) +
  theme_minimal()

ggsave(file.path(figures, "mm_hist_policy.png"), p_mm_hist_policy, dpi = 300)

#boxplot by income (base)
p_mm_box_base_income <- ggplot(df_mm, aes(income_bin, mm_logSum_base)) +
  geom_boxplot(outlier.alpha = 0.3) +
  labs(
    title = "Multi-modal Accessibility (Base) by Income",
    x = "Income",
    y = "Multi-modal logsum accessibility (base)"
  ) +
  theme_minimal()

ggsave(file.path(figures, "mm_box_base_by_income.png"), p_mm_box_base_income, dpi = 300)

#histsogram Δ mm acc
q_low  <- quantile(df_mm$mm_delta, 0.01, na.rm = TRUE)
q_high <- quantile(df_mm$mm_delta, 0.99, na.rm = TRUE)

df_delta_trim <- df_mm %>%
  filter(
    mm_delta >= q_low,
    mm_delta <= q_high
  )
p_mm_hist_delta_trim <- ggplot(df_delta_trim, aes(x = mm_delta)) +
  geom_histogram(bins = 20, na.rm = TRUE) +
  scale_x_continuous(
    breaks = scales::breaks_width(0.5)  # adjust if needed
  ) +
  labs(
    title = "Histogram of Δ Multimodal accessibility (policy − base)\n(1–99% trimmed)",
    x = "Δ Multimodal accessibility",
    y = "Count"
  ) +
  theme()

ggsave(
  file.path(figures, "mm_hist_delta_trimmed_1_99.png"),
  p_mm_hist_delta_trim,
  dpi = 300
)

#car only Δ by income (car_delta)
p_car_income <- ggplot(df_mm, aes(income_bin, car_delta)) +
  geom_boxplot(outlier.alpha = 0.3, na.rm = TRUE) +
  labs(
    title = "Δ Car-only Accessibility (Policy − Base) by Income",
    x = "Income",
    y = "Δ Car-only logsum accessibility"
  ) +
  theme_minimal()

ggsave(file.path(figures, "car_box_delta_by_income.png"), p_car_income, dpi = 300)

#histogram car-share (base)
p_carshare_hist <- df_mm %>%
  filter(carAvail == "always") %>%
  ggplot(aes(x = car_share_base)) +
  geom_histogram(bins = 40, na.rm = TRUE) +
  labs(
    title = "Histogram of Car Share (Base)\n(Car-available population only)",
    x = "Car share of expSum (base)",
    y = "Count"
  ) +
  theme_minimal()

ggsave(file.path(figures, "carshare_hist_base_carAvail_only.png"),
       p_carshare_hist,
       dpi = 300,
       width = 8,
       height = 6)


#scatterplot car-share (base) vs mm Δ
p_scatter_carshare_mmDelta <- df_mm %>%
  filter(carAvail == "always") %>%
  ggplot(aes(x = car_share_base, y = mm_delta)) +
  geom_point(alpha = 0.3, na.rm = TRUE) +
  geom_smooth(method = "lm", se = TRUE, na.rm = TRUE) +
  labs(
    title = "Car Share (Base) vs Δ Multi-modal Accessibility\n(Car-available population only)",
    x = "Car share of expSum (base)",
    y = "Δ Multi-modal logsum accessibility (policy − base)"
  ) +
  theme_minimal()

ggsave(file.path(figures, "scatter_carshare_base_vs_mm_delta_carAvail_only.png"),
       p_scatter_carshare_mmDelta,
       dpi = 300,
       width = 8,
       height = 6)

# =========================
# Population distribution by income (count + share%)
# =========================

income_counts <- df_mm %>%
  count(income_bin) %>%
  mutate(
    share = n / sum(n),
    label = paste0(n, "\n(", scales::percent(share, accuracy = 0.1), ")")
  )

p_pop_income <- ggplot(income_counts, aes(x = income_bin, y = n)) +
  geom_col() +
  geom_text(aes(label = label),
            vjust = -0.2,
            size = 3.5) +
  scale_y_continuous(
    expand = expansion(mult = c(0, 0.1))  # adds 10% headroom
  ) +
  labs(
    title = "Population distribution by income",
    x = "Income",
    y = "Population count"
  ) +
  theme()

ggsave(
  file.path(figures, "pop_dist_by_income.png"),
  p_pop_income,
  dpi = 300,
  width = 8,
  height = 6
)

# =========================
# Population distribution by district (count + share%)
# =========================

district_counts <- df_mm %>%
  count(district_ord) %>%
  mutate(
    share = n / sum(n),
    label = paste0(n, "\n(", scales::percent(share, accuracy = 0.1), ")")
  )

p_pop_district <- ggplot(district_counts, aes(x = district_ord, y = n)) +
  geom_col() +
  geom_text(aes(label = label),
            vjust = -0.2,
            size = 3) +
  scale_y_continuous(
    expand = expansion(mult = c(0, 0.1))  # adds 10% headroom
  ) +
  labs(
    title = "Population distribution by district",
    x = "District",
    y = "Population count"
  ) +
  theme(axis.text.x = element_text(size = 10, angle = 30, hjust = 1))

ggsave(
  file.path(figures, "pop_dist_by_district.png"),
  p_pop_district,
  dpi = 300,
  width = 10,
  height = 5
)

# =========================
# Histogram teleported walk accessibility (zoomed 1–99%) can just copy and adjust for pt
# =========================

# compute percentile thresholds
q_low  <- quantile(df_mm$twalk_logsum, 0.01, na.rm = TRUE)
q_high <- quantile(df_mm$twalk_logsum, 0.99, na.rm = TRUE)

p_twalk_hist_zoom <- ggplot(df_mm, aes(twalk_logsum)) +
  geom_histogram(bins = 40, na.rm = TRUE) +
  coord_cartesian(xlim = c(q_low, q_high)) +
  labs(
    title = "Histogram of teleported walk accessibility (Zoomed 1–99%)",
    x = "Teleported walk accessibility",
    y = "Count"
  ) +
  theme()

ggsave(
  file.path(figures, "twalk_hist_zoom_1_99.png"),
  p_twalk_hist_zoom,
  dpi = 300,
  width = 8,
  height = 6
)

#PT HISTOGRAM HERE!!!
q_low  <- quantile(df_mm$pt_logSum, 0.01, na.rm = TRUE)
q_high <- quantile(df_mm$pt_logSum, 0.99, na.rm = TRUE)

p_pt_hist_zoom <- ggplot(df_mm, aes(x = pt_logSum)) +
  geom_histogram(bins = 40, na.rm = TRUE) +
  coord_cartesian(xlim = c(q_low, q_high)) +
  labs(
    title = "Histogram of PT accessibility (Zoomed 1–99%)",
    x = "PT accessibility",
    y = "Count"
  ) +
  theme()

ggsave(
  file.path(figures, "pt_hist_zoom_1_99.png"),
  p_pt_hist_zoom,
  dpi = 300,
  width = 8,
  height = 6
)


# =========================
# Comparable histograms (Base vs Policy)
# Same trimming + same axis scaling
# =========================

# --- shared trimming range (based on both distributions together) ---
x_low  <- quantile(
  c(df_mm$mm_logSum_base, df_mm$mm_logSum_policy),
  0.01, na.rm = TRUE
)

x_high <- quantile(
  c(df_mm$mm_logSum_base, df_mm$mm_logSum_policy),
  0.99, na.rm = TRUE
)

# --- trim both datasets using SAME bounds ---
df_base_trim <- df_mm %>%
  filter(mm_logSum_base >= x_low,
         mm_logSum_base <= x_high)

df_policy_trim <- df_mm %>%
  filter(mm_logSum_policy >= x_low,
         mm_logSum_policy <= x_high)

# --- identical bin settings ---
bins <- 20

# compute shared y-axis maximum
h_base <- hist(df_base_trim$mm_logSum_base,
               breaks = bins,
               plot = FALSE)

h_policy <- hist(df_policy_trim$mm_logSum_policy,
                 breaks = bins,
                 plot = FALSE)

y_max <- max(h_base$counts, h_policy$counts)*1.5

# ---------------- Base ----------------
p_mm_hist_base_trim <- ggplot(df_base_trim,
                              aes(x = mm_logSum_base)) +
  geom_histogram(bins = bins, na.rm = TRUE) +
  coord_cartesian(xlim = c(x_low, x_high),
                  ylim = c(0, y_max)) +
  scale_x_continuous(
    breaks = scales::breaks_width(5)
  ) +
  labs(
    title = "Histogram of multimodal accessibility (base)\n(1–99% trimmed)",
    x = "Multimodal accessibility (base)",
    y = "Count"
  ) +
  theme()

ggsave(
  file.path(figures, "mm_hist_base_trimmed_1_99.png"),
  p_mm_hist_base_trim,
  dpi = 300,
  width = 8,
  height = 6
)

# ---------------- Policy ----------------
p_mm_hist_policy_trim <- ggplot(df_policy_trim,
                                aes(x = mm_logSum_policy)) +
  geom_histogram(bins = bins, na.rm = TRUE) +
  coord_cartesian(xlim = c(x_low, x_high),
                  ylim = c(0, y_max)) +
  scale_x_continuous(
    breaks = scales::breaks_width(5)
  ) +
  labs(
    title = "Histogram of multimodal accessibility (policy)\n(1–99% trimmed)",
    x = "Multimodal accessibility (policy)",
    y = "Count"
  ) +
  theme()

ggsave(
  file.path(figures, "mm_hist_policy_trimmed_1_99.png"),
  p_mm_hist_policy_trim,
  dpi = 300,
  width = 8,
  height = 6
)

# =========================
# Median multimodal logsum by income (Base vs Policy)
# =========================

# 1) Aggregate to income level
df_income_logsum <- df_mm %>%
  group_by(income_bin) %>%
  summarise(
    mm_logSum_base_avg   = median(mm_logSum_base,   na.rm = TRUE),
    mm_logSum_policy_avg = median(mm_logSum_policy, na.rm = TRUE),
    .groups = "drop"
  )

# 2) Long format for ggplot
df_income_logsum_long <- df_income_logsum %>%
  tidyr::pivot_longer(
    cols = c(mm_logSum_base_avg, mm_logSum_policy_avg),
    names_to = "scenario",
    values_to = "mm_logSum"
  ) %>%
  mutate(
    scenario = factor(
      scenario,
      levels = c("mm_logSum_base_avg", "mm_logSum_policy_avg"),
      labels = c("Base", "Policy")
    )
  )

# 3) Side-by-side bars
p_income_logsum_bar <- ggplot(
  df_income_logsum_long,
  aes(x = income_bin, y = mm_logSum, fill = scenario)
) +
  geom_col(position = position_dodge(width = 0.8)) +
  labs(
    title = "Median multimodal accessibility (logsum) by Income",
    x = "Income",
    y = "Median multimodal logsum accessibility",
    fill = "Scenario"
  ) +
  theme()

ggsave(
  file.path(figures, "mm_logSum_base_vs_policy_by_income.png"),
  p_income_logsum_bar,
  dpi = 300,
  width = 9,
  height = 6
)


# =========================
# Median accessibility by income and mode
# Base vs Policy case
# =========================

# Requires tidyr for pivot_longer()
suppressPackageStartupMessages({
  library(tidyr)
})

# Build long-format dataset with one row per person/mode/scenario
# Notes:
# - Walk and PT are unchanged between base and policy in the current setup.
# - Car changes between base and policy.
# - Multimodal uses mm_logSum_base and mm_logSum_policy.
df_income_mode_access <- df_mm %>%
  select(
    personId,
    income_bin,
    twalk_logsum,
    pt_logSum,
    car_logSum_base,
    car_logSum_policy,
    mm_logSum_base,
    mm_logSum_policy
  ) %>%
  pivot_longer(
    cols = c(
      twalk_logsum,
      pt_logSum,
      car_logSum_base,
      car_logSum_policy,
      mm_logSum_base,
      mm_logSum_policy
    ),
    names_to = "accessibility_type",
    values_to = "accessibility"
  ) %>%
  mutate(
    scenario = case_when(
      accessibility_type %in% c("car_logSum_policy", "mm_logSum_policy") ~ "Policy",
      TRUE ~ "Base"
    ),
    mode = case_when(
      accessibility_type == "twalk_logsum" ~ "Walk",
      accessibility_type == "pt_logSum" ~ "PT",
      accessibility_type %in% c("car_logSum_base", "car_logSum_policy") ~ "Car",
      accessibility_type %in% c("mm_logSum_base", "mm_logSum_policy") ~ "Multimodal",
      TRUE ~ NA_character_
    ),
    scenario = factor(scenario, levels = c("Base", "Policy")),
    mode = factor(mode, levels = c("Car", "PT", "Walk", "Multimodal"))
  ) %>%
  filter(!is.na(income_bin), !is.na(mode), !is.na(accessibility))

# Because walk and PT are unchanged between scenarios, duplicate them into Policy
# so the policy plot contains all four requested modes.
df_income_mode_access_policy_extra <- df_income_mode_access %>%
  filter(scenario == "Base", mode %in% c("Walk", "PT")) %>%
  mutate(scenario = factor("Policy", levels = c("Base", "Policy")))

df_income_mode_access_plot <- bind_rows(
  df_income_mode_access,
  df_income_mode_access_policy_extra
)

# Aggregate to median accessibility per income group, mode, and scenario
median_income_mode_access <- df_income_mode_access_plot %>%
  group_by(scenario, income_bin, mode) %>%
  summarise(
    median_accessibility = median(accessibility, na.rm = TRUE),
    .groups = "drop"
  )

write_csv(
  median_income_mode_access,
  file.path(tables, "median_accessibility_by_income_mode_scenario.csv")
)

# Base case plot
p_median_income_mode_base <- median_income_mode_access %>%
  filter(scenario == "Base") %>%
  ggplot(aes(x = income_bin, y = median_accessibility, fill = mode)) +
  geom_col(position = position_dodge(width = 0.8), width = 0.7) +
  labs(
    title = "Median accessibility by income and mode (Base)",
    x = "Income",
    y = "Median accessibility",
    fill = "Mode"
  ) +
  theme(
    axis.text = element_text(size = 14),
    axis.title = element_text(size = 14),
    legend.text = element_text(size = 14),
    legend.title = element_text(size = 14)
  )

ggsave(
  file.path(figures, "median_accessibility_by_income_mode_base.png"),
  p_median_income_mode_base,
  dpi = 300,
  width = 9,
  height = 6
)

# Policy case plot
p_median_income_mode_policy <- median_income_mode_access %>%
  filter(scenario == "Policy") %>%
  ggplot(aes(x = income_bin, y = median_accessibility, fill = mode)) +
  geom_col(position = position_dodge(width = 0.8), width = 0.7) +
  labs(
    title = "Median accessibility by income and mode (Policy)",
    x = "Income",
    y = "Median accessibility",
    fill = "Mode"
  ) +
  theme(
    axis.text = element_text(size = 14),
    axis.title = element_text(size = 14),
    legend.text = element_text(size = 14),
    legend.title = element_text(size = 14)
  )

ggsave(
  file.path(figures, "median_accessibility_by_income_mode_policy.png"),
  p_median_income_mode_policy,
  dpi = 300,
  width = 9,
  height = 6
)

# Optional combined/faceted version for quick comparison
p_median_income_mode_base_policy <- ggplot(
  median_income_mode_access,
  aes(x = income_bin, y = median_accessibility, fill = mode)
) +
  geom_col(position = position_dodge(width = 0.8), width = 0.7) +
  facet_wrap(~ scenario, ncol = 1) +
  labs(
    title = "Median accessibility by income and mode: Base vs Policy",
    x = "Income",
    y = "Median accessibility",
    fill = "Mode"
  ) +
  theme(
    axis.text = element_text(size = 14),
    axis.title = element_text(size = 14),
    legend.text = element_text(size = 14),
    legend.title = element_text(size = 14)
  )

ggsave(
  file.path(figures, "median_accessibility_by_income_mode_base_policy.png"),
  p_median_income_mode_base_policy,
  dpi = 300,
  width = 9,
  height = 9
)
