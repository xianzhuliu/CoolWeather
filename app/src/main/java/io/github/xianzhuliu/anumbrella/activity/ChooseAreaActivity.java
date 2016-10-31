package io.github.xianzhuliu.anumbrella.activity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.github.xianzhuliu.anumbrella.R;
import io.github.xianzhuliu.anumbrella.db.AnUmbrellaDB;
import io.github.xianzhuliu.anumbrella.model.City;
import io.github.xianzhuliu.anumbrella.model.MyCity;
import io.github.xianzhuliu.anumbrella.util.HttpCallbackListener;
import io.github.xianzhuliu.anumbrella.util.HttpUtil;
import io.github.xianzhuliu.anumbrella.util.Utility;

/**
 * Created by LiuXianzhu on 19/10/2016.
 * Contact: liuxianzhu0221@gmail.com
 */

public class ChooseAreaActivity extends Activity {
    private static final String TAG = "ChooseAreaActivity";
    public static final int LEVEL_PROVINCE = 0;
    public static final int LEVEL_CITY = 1;
    public static final int LEVEL_COUNTY = 2;

    private ProgressDialog progressDialog;
    private TextView titleText;
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private AnUmbrellaDB anUmbrellaDB;
    private List<String> dataList = new ArrayList<>();
    private List<City> cityList;
    private List<City> countyList = new ArrayList<>();
    private String selectedProvince;
    private String selectedCity;
    private int currentLevel;
    private boolean isFromWeatherActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.choose_area);
        listView = (ListView) findViewById(R.id.list_view);
        titleText = (TextView) findViewById(R.id.title_text);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, dataList);
        listView.setAdapter(adapter);
        anUmbrellaDB = AnUmbrellaDB.getInstance(this);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (currentLevel == LEVEL_PROVINCE) {
                    selectedProvince = dataList.get(position);
                    queryCities();
                } else if (currentLevel == LEVEL_CITY) {
                    selectedCity = dataList.get(position);
                    queryCounties();
                } else if (currentLevel == LEVEL_COUNTY) {
                    City city = countyList.get(position);
                    final int myCityId = (int) anUmbrellaDB.saveMyCity(city);
                    List<MyCity> myCityList = anUmbrellaDB.loadMyCities();
                    for (MyCity myCity : myCityList) {
                        if (myCity.getCityId() == myCityId) {
                            Toast.makeText(ChooseAreaActivity.this, "已经存在" + anUmbrellaDB.findCityById(myCityId) +
                                    "，请不要重复添加·_·", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    String address = "https://api.heweather.com/x3/weather?cityid=" + city.getCityCode() +
                            "&key=b722b324cb4a43c39bd1ca487cc89d7c";
                    HttpUtil.sendOkHttp(address, new HttpCallbackListener() {
                        @Override
                        public void onFinish(String response) {
                            try {
                                if (!Utility.handleWeatherResponse(ChooseAreaActivity.this, new JSONObject(response),
                                        myCityId)) {

                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(ChooseAreaActivity.this, "貌似网络出问题了~_~", Toast
                                                    .LENGTH_SHORT).show();
                                        }
                                    });
                                } else {
                                    finish();
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onError(Exception e) {
                            finish();
                        }
                    });

                }
            }
        });
        queryProvinces();
    }

    private void queryCounties() {
        if (cityList == null || cityList.isEmpty()) {
            cityList = anUmbrellaDB.loadCities();
        }
        if (cityList.size() > 0) {
            dataList.clear();
            LinkedHashSet<String> set = new LinkedHashSet<>();
            for (City city : cityList) {
                if (city.getCityName().equals(selectedCity)) {
                    set.add(city.getCountyName());
                    countyList.add(city);
                }
            }
            dataList.addAll(set);
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            titleText.setText(selectedCity);
            currentLevel = LEVEL_COUNTY;
        } else {
            queryFromServer("county");
        }
    }


    private void queryCities() {
        if (cityList == null || cityList.isEmpty()) {
            cityList = anUmbrellaDB.loadCities();
        }
        if (cityList.size() > 0) {
            dataList.clear();
            Set<String> set = new HashSet<>();
            for (City city : cityList) {
                if (city.getProvinceName().equals(selectedProvince)) {
                    set.add(city.getCityName());
                }
            }
            dataList.addAll(set);
            Collator collatorChinese = Collator.getInstance(Locale.CHINESE);
            Collections.sort(dataList, collatorChinese);
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            titleText.setText(selectedProvince);
            currentLevel = LEVEL_CITY;
        } else {
            queryFromServer("city");
        }
    }

    private void queryProvinces() {
        if (cityList == null || cityList.isEmpty()) {
            cityList = anUmbrellaDB.loadCities();
        }
        if (cityList.size() > 0 && !AnUmbrellaDB.update) {
            dataList.clear();
            Set<String> set = new LinkedHashSet<>();
            for (City city : cityList) {
                set.add(city.getProvinceName());
            }
            dataList.addAll(set);
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            titleText.setText("中国");
            currentLevel = LEVEL_PROVINCE;
        } else {
            queryFromServer("province");
            AnUmbrellaDB.update = false;
        }
    }

    private void queryFromServer(final String type) {
        showProgressDialog();
        HttpUtil.getCitiesFromFile(ChooseAreaActivity.this, new HttpCallbackListener() {
            @Override
            public void onFinish(String response) {
                boolean result;
                result = Utility.handleCitiesResponse(anUmbrellaDB, response);
                if (result) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            closeProgressDialog();
                            if ("province".equals(type)) {
                                queryProvinces();
                            } else if ("city".equals(type)) {
                                queryCities();
                            } else if ("county".equals(type)) {
                                queryCounties();
                            }
                        }
                    });
                }
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        closeProgressDialog();
                        Toast.makeText(ChooseAreaActivity.this, "加载失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void showProgressDialog() {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("正在加载城市信息...");
            progressDialog.setCanceledOnTouchOutside(false);
        }
        progressDialog.show();
    }

    private void closeProgressDialog() {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }

    @Override
    public void onBackPressed() {
        if (currentLevel == LEVEL_COUNTY) {
            queryCities();
        } else if (currentLevel == LEVEL_CITY) {
            queryProvinces();
        } else {
            super.onBackPressed();
        }
    }
}