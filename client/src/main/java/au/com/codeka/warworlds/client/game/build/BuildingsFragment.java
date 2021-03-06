package au.com.codeka.warworlds.client.game.build;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import au.com.codeka.warworlds.client.R;
import au.com.codeka.warworlds.common.proto.BuildRequest;
import au.com.codeka.warworlds.common.sim.DesignHelper;
import au.com.codeka.warworlds.common.proto.Building;
import au.com.codeka.warworlds.common.proto.Colony;
import au.com.codeka.warworlds.common.proto.Design;
import au.com.codeka.warworlds.common.proto.Star;

public class BuildingsFragment extends BuildFragment.BaseTabFragment {
  private static final long REFRESH_DELAY_MS = 1000L;

  private final Handler handler = new Handler();
  private BuildingListAdapter adapter;

  @Override
  protected int getViewResourceId() {
    return R.layout.frag_build_buildings;
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    adapter = new BuildingListAdapter();
    adapter.setColony(getStar(), getColony());
    ListView buildingsList = (ListView) view.findViewById(R.id.building_list);
    buildingsList.setAdapter(adapter);
    buildingsList.setOnItemClickListener((parent, v, position, id) -> {
      ItemEntry entry = (ItemEntry) adapter.getItem(position);
      if (entry.building == null && entry.buildRequest == null) {
        getBuildFragment().showBuildSheet(entry.design);
      } else if (entry.building != null && entry.buildRequest == null) {
        // TODO: upgrade
        getBuildFragment().showBuildSheet(entry.design);
      }
    });
  }

  @Override
  public void onResume() {
    super.onResume();
    handler.postDelayed(refreshRunnable, REFRESH_DELAY_MS);
  }

  @Override
  public void onPause() {
    super.onPause();
    handler.removeCallbacks(refreshRunnable);
  }

  /** This adapter is used to populate a list of buildings in a list view. */
  private class BuildingListAdapter extends BaseAdapter {
    private ArrayList<ItemEntry> entries;

    private static final int HEADING_TYPE = 0;
    private static final int EXISTING_BUILDING_TYPE = 1;
    private static final int NEW_BUILDING_TYPE = 2;

    public void setColony(Star star, Colony colony) {
      entries = new ArrayList<>();

      List<Building> buildings = colony.buildings;
      if (buildings == null) {
        buildings = new ArrayList<>();
      }

      ArrayList<ItemEntry> existingBuildingEntries = new ArrayList<>();
      for (Building b : buildings) {
        ItemEntry entry = new ItemEntry();
        entry.building = b;
        // if the building is being upgraded (i.e. if there's a build request that
        // references this building) then add the build request as well
        for (BuildRequest br : BuildHelper.getBuildRequests(star)) {
//          if (br.existing_building_id != null && br.existing_building_id.equals(b.id)) {
//            entry.buildRequest = (BuildRequest) br;
//          }
        }
        existingBuildingEntries.add(entry);
      }

      for (BuildRequest br : colony.build_requests) {
        Design design = DesignHelper.getDesign(br.design_type);
        if (design.design_kind == Design.DesignKind.BUILDING
          /*&& br.existing_building_id == null*/) {
          ItemEntry entry = new ItemEntry();
          entry.buildRequest = br;
          existingBuildingEntries.add(entry);
        }
      }

      Collections.sort(existingBuildingEntries, new Comparator<ItemEntry>() {
        @Override
        public int compare(ItemEntry lhs, ItemEntry rhs) {
          Design.DesignType a = (lhs.building != null
              ? lhs.building.design_type : lhs.buildRequest.design_type);
          Design.DesignType b = (rhs.building != null
              ? rhs.building.design_type : rhs.buildRequest.design_type);
          return a.compareTo(b);
        }
      });

      ItemEntry title = new ItemEntry();
      title.title = "New Buildings";
      entries.add(title);

      for (Design design : DesignHelper.getDesigns(Design.DesignKind.BUILDING)) {
        if (design.max_per_colony != null && design.max_per_colony > 0) {
          int numExisting = 0;
          for (ItemEntry e : existingBuildingEntries) {
            if (e.building != null) {
              if (e.building.design_type.equals(design.type)) {
                numExisting ++;
              }
            } else if (e.buildRequest != null) {
              if (e.buildRequest.design_type.equals(design.type)) {
                numExisting ++;
              }
            }
          }
          if (numExisting >= design.max_per_colony) {
            continue;
          }
        }
       // if (bd.getMaxPerEmpire() > 0) {
       //   int numExisting = BuildManager.i.getTotalBuildingsInEmpire(bd.getID());
       //   // If you're building one, we'll still think it's OK to build again, but it's
       //   // actually going to be blocked by the server.
       //   if (numExisting >= bd.getMaxPerEmpire()) {
       //     continue;
       //   }
       // }
        ItemEntry entry = new ItemEntry();
        entry.design = design;
        entries.add(entry);
      }

      title = new ItemEntry();
      title.title = "Existing Buildings";
      entries.add(title);

      for (ItemEntry entry : existingBuildingEntries) {
        entries.add(entry);
      }

      notifyDataSetChanged();
    }

    /**
     * We have three types of items, the "headings", the list of existing buildings and the list of
     * building designs.
     */
    @Override
    public int getViewTypeCount() {
      return 3;
    }

    @Override
    public int getItemViewType(int position) {
      if (entries == null)
        return 0;

      if (entries.get(position).title != null)
        return HEADING_TYPE;
      if (entries.get(position).design != null)
        return NEW_BUILDING_TYPE;
      return EXISTING_BUILDING_TYPE;
    }

    @Override
    public boolean isEnabled(int position) {
      if (getItemViewType(position) == HEADING_TYPE) {
        return false;
      }

      // also, if it's an existing building that's at the max level it can't be
      // upgraded any more, so also disabled.
      ItemEntry entry = entries.get(position);
      if (entry.building != null) {
        int maxUpgrades = DesignHelper.getDesign(entry.building.design_type).upgrades.size();
        if (entry.building.level > maxUpgrades) {
          return false;
        }
      }

      return true;
    }

    @Override
    public int getCount() {
      if (entries == null)
        return 0;

      return entries.size();
    }

    @Override
    public Object getItem(int position) {
      if (entries == null)
        return null;

      return entries.get(position);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      View view = convertView;
      if (view == null) {
        LayoutInflater inflater =
            (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        int viewType = getItemViewType(position);
        if (viewType == HEADING_TYPE) {
          view = new TextView(getContext());
        } else {
          view = inflater.inflate(R.layout.ctrl_build_design, parent, false);
        }
      }

      ItemEntry entry = entries.get(position);
      if (entry.title != null) {
        TextView tv = (TextView) view;
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setText(entry.title);
      } else if (entry.building != null || entry.buildRequest != null) {
        // existing building/upgrading building
        ImageView icon = (ImageView) view.findViewById(R.id.building_icon);
        TextView row1 = (TextView) view.findViewById(R.id.design_row1);
        TextView row2 = (TextView) view.findViewById(R.id.design_row2);
        TextView row3 = (TextView) view.findViewById(R.id.design_row3);
        TextView level = (TextView) view.findViewById(R.id.build_level);
        TextView levelLabel = (TextView) view.findViewById(R.id.build_level_label);
        ProgressBar progress = (ProgressBar) view.findViewById(R.id.build_progress);
        TextView notes = (TextView) view.findViewById(R.id.notes);

        Building building = entry.building;
        BuildRequest buildRequest = entry.buildRequest;
        Design design = DesignHelper.getDesign(
            (building != null ? building.design_type : buildRequest.design_type));

        BuildHelper.setDesignIcon(design, icon);
        int numUpgrades = design.upgrades.size();

        if (numUpgrades == 0 || building == null) {
          level.setVisibility(View.GONE);
          levelLabel.setVisibility(View.GONE);
        } else {
          level.setText(String.format(Locale.US, "%d", building.level));
          level.setVisibility(View.VISIBLE);
          levelLabel.setVisibility(View.VISIBLE);
        }

        row1.setText(design.display_name);
        if (buildRequest != null) {
          String verb = (building == null ? "Building" : "Upgrading");
          row2.setText(Html.fromHtml(String.format(Locale.ENGLISH,
              "<font color=\"#0c6476\">%s:</font> %d %%, %s left",
              verb, Math.round(buildRequest.progress * 100.0f),
              BuildHelper.formatTimeRemaining(buildRequest))));

          row3.setVisibility(View.GONE);
          progress.setVisibility(View.VISIBLE);
          progress.setProgress(Math.round(buildRequest.progress * 100.0f));
        } else if (building != null) {
          if (numUpgrades < building.level) {
            row2.setText(getContext().getString(R.string.no_more_upgrades));
            row3.setVisibility(View.GONE);
            progress.setVisibility(View.GONE);
          } else {
            progress.setVisibility(View.GONE);

            String requiredHtml =
                DesignHelper.getDependenciesHtml(getColony(), design, building.level + 1);
            row2.setText(Html.fromHtml(requiredHtml));

            row3.setVisibility(View.GONE);
          }
        }

        if (building != null && building.notes != null) {
          notes.setText(building.notes);
          notes.setVisibility(View.VISIBLE);
        } /*else if (buildRequest != null && buildRequest.notes != null) {
          notes.setText(buildRequest.getNotes());
          notes.setVisibility(View.VISIBLE);
        } */else {
          notes.setText("");
          notes.setVisibility(View.GONE);
        }
      } else {
        // new building
        ImageView icon = (ImageView) view.findViewById(R.id.building_icon);
        TextView row1 = (TextView) view.findViewById(R.id.design_row1);
        TextView row2 = (TextView) view.findViewById(R.id.design_row2);
        TextView row3 = (TextView) view.findViewById(R.id.design_row3);

        view.findViewById(R.id.build_progress).setVisibility(View.GONE);
        view.findViewById(R.id.build_level).setVisibility(View.GONE);
        view.findViewById(R.id.build_level_label).setVisibility(View.GONE);
        view.findViewById(R.id.notes).setVisibility(View.GONE);

        Design design = entries.get(position).design;
        BuildHelper.setDesignIcon(design, icon);

        row1.setText(design.display_name);
        String requiredHtml = DesignHelper.getDependenciesHtml(getColony(), design);
        row2.setText(Html.fromHtml(requiredHtml));

        row3.setVisibility(View.GONE);
      }

      return view;
    }
  }

  private final Runnable refreshRunnable = new Runnable() {
    @Override
    public void run() {
      if (!isResumed()) {
        return;
      }

      adapter.notifyDataSetChanged();

      handler.postDelayed(refreshRunnable, REFRESH_DELAY_MS);
    }
  };

  static class ItemEntry {
    String title;
    BuildRequest buildRequest;
    Building building;
    Design design;
  }
}
