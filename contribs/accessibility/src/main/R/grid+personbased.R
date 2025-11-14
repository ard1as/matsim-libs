# install.packages("sf")
library(tmap)
library(sf)
library(dplyr)
library(tidyverse)


#load data
grid_df <- read.csv("grid_accessibilities (200m).csv")
persons_df <- read.csv("person_based_accessibility (walk + default).csv")

#convert to sf object
grid_sf <- st_as_sf(grid_df, coords = c("xcoord", "ycoord"), crs = 25832)
persons_sf <- st_as_sf(persons_df, coords = c("homeX", "homeY"), crs = 25832)

xx <- persons_sf %>% st_join(grid_sf, join = st_nearest_feature) %>% 
  mutate(diff = accessibility - teleportedWalk_accessibility)
ggplot(xx)+geom_point(aes(accessibility, teleportedWalk_accessibility), alpha=0.1)+xlim(-30,0)+ylim(-30,0) #add distance column 


xx %>% view()
tmap_mode("view")
# tm_shape(grid_sf) + tm_symbols(col = "teleportedWalk_accessibility", palette = viridis(10), breaks = seq(-40,0,5), shape = 22, size = 1) + 
  tm_shape(xx) + tm_dots(col = "accessibility", palette = viridis(10), title = "Person-Based",breaks = seq(-40,0,5), size = .5)

# tm_shape(xx) + tm_dots(col = "diff", palette = "RdBu",midpoint = 0,  breaks = seq(-1,1,.1), title = "Person-Based - Grid Based", size = .5)
  
#find nearest person to each grid
nearest_idx <- st_nearest_feature(grid_sf, persons_sf)

#attach attributes of the nearest person
merged <- bind_cols(
  grid_sf,
  persons_sf[nearest_idx, ] %>% st_drop_geometry()
)
merged$nearest_idx <- nearest_idx

#distance to nearest person
merged$distance_m <- st_distance(grid_sf, persons_sf[nearest_idx, ], by_element = TRUE)
merged$distance_m <- as.numeric(merged$distance_m)

#keep only matches within 100m
merged_filtered <- merged %>% 
  filter(distance_m <= 100) %>%
  mutate(distance_m = round(distance_m, 2),
         diff = round(abs(teleportedWalk_accessibility - accessibility), 2))

#extract coordinates back into numeric columns
merged_with_coords <- merged_filtered %>%
  mutate(
    xcoord = st_coordinates(merged_filtered)[, 1],
    ycoord = st_coordinates(merged_filtered)[, 2],
    homeX = persons_df$homeX[merged_filtered$nearest_idx],
    homeY = persons_df$homeY[merged_filtered$nearest_idx]
  )

#reorder+remove columns
col_order <- c("id", "xcoord", "ycoord", "personId", "homeX", "homeY", "mode", "age", "sex", "economic_status", "accessibility", "teleportedWalk_accessibility", "diff", "distance_m")

#final dataframe
merged_final <- merged_with_coords[, col_order] %>% st_drop_geometry()

#write csv
write.csv(merged_final, "merged_accessibility_output.csv", row.names = FALSE)

