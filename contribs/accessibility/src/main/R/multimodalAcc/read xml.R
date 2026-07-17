library(tidyverse)
#persons <- read_delim("D:/git/public-svn/matsim/scenarios/countries/de/berlin/berlin-v6.4/output/berlin-v6.4-10pct/berlin-v6.4.output_persons.csv.gz", delim = ";")
persons <- read_delim("D:/git/public-svn/matsim/scenarios/countries/de/berlin/projects/fabilut/output-1pct/base/berlin-v6.3.output_persons.csv.gz", delim = ";")


persons %>%
  filter(!is.na(income), !is.na(carAvail)) %>% 
  mutate(
    income = case_when(
      income %in% c(499,500) ~ "500",
      income == 900  ~ "900",
      income == 1500 ~ "1500",
      income == 2000 ~ "2000",
      income == 2600 ~ "2600",
      income == 3000 ~ "3000",
      income == 3600 ~ "3600",
      income == 4600 ~ "4600",
      income == 5600 ~ "5600",
      TRUE          ~ "5600+"
    ),
    income = factor(
      income,
      levels = c("500","900","1500","2000","2600","3000","3600","4600","5600"),
      ordered = TRUE
    )
  ) %>% 
  group_by(income, carAvail) %>% 
  count() %>% 
  group_by(income) %>% 
  mutate(share = n / sum(n)) %>%  
  ggplot() + 
  geom_col(aes(income, share, fill = carAvail)) +
  theme(
    axis.text = element_text(size = 13),
    axis.title = element_text(size = 14),
    legend.text = element_text(size = 14),
   legend.title = element_text(size = 14)
  )

persons %>%
  filter(!is.na(income)) %>% 
  mutate(
    income = case_when(
      income %in% c(499,500) ~ "500",
      income == 900  ~ "900",
      income == 1500 ~ "1500",
      income == 2000 ~ "2000",
      income == 2600 ~ "2600",
      income == 3000 ~ "3000",
      income == 3600 ~ "3600",
      income == 4600 ~ "4600",
      income == 5600 ~ "5600",
      TRUE          ~ "5600+"
    ),
    income = factor(
      income,
      levels = c("500","900","1500","2000","2600","3000","3600","4600","5600"),
      ordered = TRUE
    )
  ) %>% 
  count(income) %>% 
  ggplot(aes(income, n)) +
  geom_col(width = 0.75, fill = "steelblue") +
  labs(x = "Income (€)", y = "Population count") +
  theme(
    axis.text = element_text(size = 14),
    axis.title = element_text(size = 14)
  )
