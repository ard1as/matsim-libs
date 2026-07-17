library(dplyr)
library(readr)

# Read files
base <- read_csv("vabali_base.csv")
policy <- read_csv("vabali_policy.csv")

# Transform expSum -> car accessibility
base <- base %>%
  mutate(car_acc = log(expSum)) %>%
  select(-expSum)

policy <- policy %>%
  mutate(car_acc = log(expSum)) %>%
  select(-expSum)

# Merge
merged_df <- base %>%
  rename_with(~ paste0("base_", .x), -person) %>%
  inner_join(
    policy %>%
      rename_with(~ paste0("policy_", .x), -person),
    by = "person"
  ) %>%
  mutate(
    delta_timeCar = policy_timeCar - base_timeCar,
    delta_pct_timeCar = 100 * delta_timeCar / base_timeCar,
    
    delta_distCar = policy_distCar - base_distCar,
    delta_pct_distCar = 100 * delta_distCar / base_distCar,
    
    delta_car_acc = policy_car_acc - base_car_acc,
    delta_pct_car_acc = 100 * delta_car_acc / abs(base_car_acc)
  )

# Add demographic / spatial attributes from multimodal analysis
person_attributes <- read_csv("mmAcc.csv") %>%
  transmute(
    person = as.character(personId),
    age,
    sex,
    income_bin,
    homeX,
    homeY,
    district_ord
  )

merged_df <- merged_df %>%
  mutate(person = as.character(person)) %>%
  left_join(person_attributes, by = "person") %>%
  relocate(
    age, sex, income_bin, homeX, homeY, district_ord,
    .after = person
  )

# View in RStudio
View(merged_df)

# Optional console preview
print(merged_df)
summary(merged_df)
# Optional export
write_csv(merged_df, "deepdive_base_policy_merged.csv")
