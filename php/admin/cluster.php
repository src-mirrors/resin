<?php
/**
 * Provides the clustering information.
 */


require_once "WEB-INF/php/inc.php";

$mbeanServer = new MBeanServer();

$resin = $mbeanServer->lookup("resin:type=Resin");
$server = $mbeanServer->lookup("resin:type=Server");

$title = "Resin: Cluster";

if (! empty($server->Id))
  $title = $title . " for server " . $server->Id;
?>

<?php

display_header("cluster.php", $title, $server->Id);


?>

<h2>Server: <?= $server->Id ?></h2>

<!-- Cluster -->

<?php

$live_count = 0;

echo "<table class='data'>";
echo "<tr>";
echo "<th>Name</th>";
echo "<th>Uptime</th>";
echo "<th>Free Memory</th>";
echo "<th>CPU Avg</th>";
echo "<th>Cache Miss</th>";
echo "<th>Block Miss</th>";
echo "<th>Thread</th>";
echo "</tr>";

foreach ($resin->Clusters as $cluster) {

  if (empty($cluster->Servers))
    continue;

  echo "<tr><td class='group' colspan='8'>$cluster->Name</td></tr>\n";

  $client_names = array();
  if ($cluster->Name == $server->Cluster->Name) {
    $client_names[] = $server->Id;
  }

  foreach ($cluster->Servers as $client) {
    $client_names[] = $client->Name;
  }

  sort($client_names);

  $count = 0;
  foreach ($client_names as $name) {
    $sub_mbean_server = new MBeanServer($name);

    $sub_server = $sub_mbean_server->lookup("resin:type=Server");

    if ($count++ % 2 == 0)
      echo "<tr class='ra'>";
    else
      echo "<tr class='rb'>";

    if ($sub_server)
      echo "<td class='item'>$name</td>";
    else
      echo "<td class='itemfail'>$name</td>";

    if ($sub_server) {
      $live_count++;

      $start_time = $sub_server->StartTime->time / 1000;
      $now = time(0);
      $uptime = $now - $start_time;

      if ($uptime < 12 * 3600)
        echo "<td class='warmup'>";
      else
        echo "<td>";

      echo sprintf("%d days %02d:%02d:%02d",
                   $uptime / (24 * 3600),
                   $uptime / 3600 % 24,
                   $uptime / 60 % 60,
                   $uptime % 60) . "</td>";

      $total_memory = $sub_server->RuntimeMemory / (1024 * 1024);
      $free_memory = $sub_server->RuntimeMemoryFree / (1024 * 1024);

      if ($free_memory < $total_memory * 0.02)
        echo "<td class='fail'>";
      else if ($free_memory < $total_memory * 0.05)
        echo "<td class='warn'>";
      else
        echo "<td>";

      echo sprintf("%.2fM (%.2fM)", $free_memory, $total_memory);
      echo "</td>";

      if ($sub_server->CpuLoadAvg > 2)
        echo "<td class='fail'>";
      else if ($sub_server->CpuLoadAvg > 1)
        echo "<td class='warn'>";
      else
        echo "<td>";
      echo sprintf("%.2f", $sub_server->CpuLoadAvg);
      echo "</td>";
    }
    else {
      echo "<td></td>";
      echo "<td></td>";
      echo "<td></td>";
    }

    $proxy_cache = $sub_mbean_server->lookup("resin:type=ProxyCache");
    if ($proxy_cache) {
      echo "<td>";
      echo format_miss_ratio($proxy_cache->HitCountTotal,
                             $proxy_cache->MissCountTotal);
      echo "</td>";
    }
    else {
      echo "<td></td>";
    }

    $block_cache = $sub_mbean_server->lookup("resin:type=BlockManager");
    if ($block_cache) {
      echo "<td>" . format_miss_ratio($block_cache->HitCountTotal,
                                      $block_cache->MissCountTotal)
                  . "</td>";
    }
    else {
      echo "<td></td>";
    }

    $thread_pool = $sub_server->ThreadPool;
    if ($thread_pool) {
/*
      echo "<td>" . $thread_pool->ThreadActiveCount . "</td>";
      echo "<td>" . $thread_pool->ThreadIdleCount . "</td>";
*/
      echo "<td>" . $thread_pool->ThreadCount . "</td>";
    }
    else {
/*
      echo "<td></td>";
      echo "<td></td>";
*/
      echo "<td></td>";
    }

    echo "</tr>\n";
  }
}

echo "</table>";

if ($live_count < 2) {
  echo "<p>The cluster report requires Resin Professional and enabled remote debugging</>\n";

  echo "<h3>resin.conf</h3>";
  echo "<pre>\n";
  echo "&lt;resin xmlns='http://caucho.com/ns/resin'>\n";
  echo "  &lt;management enable-remote='true'>\n";
  echo "  ...\n";
  echo "  &lt;cluster id='...'>\n";
  echo "    ...\n";
  echo "  &lt;/cluster>\n";
  echo "&lt;/resin>\n";
  echo "</pre>\n";
}

display_footer("cluster.php");

