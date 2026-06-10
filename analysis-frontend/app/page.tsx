'use client';

import React, { useState, useRef, useMemo, useEffect, useCallback } from 'react';
import dynamic from 'next/dynamic';

const ForceGraph2D = dynamic(() => import('react-force-graph-2d'), {
  ssr: false,
});

const API_BASE = '/api/proxy';

const nodeColorMap: Record<string, string> = {
  Person: '#1f77b4',
  Container: '#32cd32',
  Component: '#ff7f0e',
  Element: '#d62728',
  SoftwareSystem: '#6200ff',
  DeploymentNode: '#964b00',
  InfrastructureNode: '#42aaff',
  ContainerInstance: '#ffc0cb',
};
const defaultColor = '#9467bd';

function downloadJSON(data: any, filename: string) {
  const json = JSON.stringify(data, null, 2);
  const blob = new Blob([json], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

function parseGraphData(graph: any) {
  if (!graph?.nodes || !Array.isArray(graph.nodes)) return null;

  const nodes = graph.nodes.map((n: any) => {
    const type = Array.isArray(n.labels) ? n.labels[0] : n.label || n.type || 'Unknown';
    const name = n.properties?.name || n.label || (Array.isArray(n.labels) ? n.labels[0] : 'Unknown');
    return {
      id: n.id,
      label: name,
      type: type,
      ...n.properties,
    };
  });

  const edges = graph.edges || [];
  const links = edges
    .filter((e: any) => e && (e.from || e.startNode) && (e.to || e.endNode))
    .map((e: any) => ({
      source: e.from ?? e.startNode,
      target: e.to ?? e.endNode,
      label: e.label || e.type,
    }));

  return { nodes, links };
}

export default function HomePage() {
  const [activeTab, setActiveTab] = useState<'visualize' | 'upload' | 'delete' | 'analysis'>('visualize');
  const [analysisType, setAnalysisType] = useState<
    'cycles' | 'singlepoints' | 'godelements' | 'pathcapacity' | 'criticalinfrastructure' | 'perimeterviolation' | null
  >(null);
  const [analysisOpen, setAnalysisOpen] = useState(false);

  const [visLoading, setVisLoading] = useState(false);
  const [visError, setVisError] = useState<string | null>(null);
  const [visResult, setVisResult] = useState<any>(null);

  const [result, setResult] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const [deleteString, setDeleteString] = useState('');

  // Поля анализа (общие)
  const [graphTag, setGraphTag] = useState('');
  const [nodeTypes, setNodeTypes] = useState('');
  const [relTypes, setRelTypes] = useState('');
  const [nodeIdentifiers, setNodeIdentifiers] = useState('');
  // Специфичные для perimeterViolation
  const [deploymentNodeIdentifier, setDeploymentNodeIdentifier] = useState('');
  const [properties, setProperties] = useState('');

  const [viewMode, setViewMode] = useState<'json' | 'graph'>('json');
  const [visViewMode, setVisViewMode] = useState<'json' | 'graph'>('graph');

  const clearMessages = () => {
    setResult(null);
    setError(null);
    setViewMode('json');
  };

  const loadVisualization = useCallback(async () => {
    setVisLoading(true);
    setVisError(null);
    try {
      const res = await fetch(`${API_BASE}/arch-graph/api/v1/elements`, {
        headers: {
          'CYPHER-QUERY': 'MATCH (n) WITH collect(DISTINCT n) AS allNodes MATCH ()-[r]-() WITH allNodes, collect(DISTINCT r) AS allRels RETURN { nodes: [n IN allNodes | { id: id(n), labels: labels(n), properties: properties(n) }], edges: [r IN allRels | { id: id(r), type: type(r), startNode: id(startNode(r)), endNode: id(endNode(r)), properties: properties(r) }] } AS graph'
        },
      });
      if (!res.ok) {
        const errText = await res.text();
        throw new Error(`Ошибка ${res.status}: ${errText}`);
      }
      const data = await res.json();
      setVisResult(data);
    } catch (e: any) {
      setVisError(e.message);
    } finally {
      setVisLoading(false);
    }
  }, []);

  useEffect(() => {
    if (activeTab === 'visualize') {
      loadVisualization();
    }
  }, [activeTab, loadVisualization]);

  const visGraphData = useMemo(() => {
    if (!visResult) return null;
    if (Array.isArray(visResult) && visResult.length > 0 && visResult[0].graph) {
      return parseGraphData(visResult[0].graph);
    }
    if (visResult.graph) {
      return parseGraphData(visResult.graph);
    }
    if (visResult.nodes) {
      return parseGraphData(visResult);
    }
    console.warn('Неизвестный формат ответа визуализации', visResult);
    return null;
  }, [visResult]);

  const analysisGraphData = useMemo(() => {
    if (!result) return null;
    try {
      const parsed = JSON.parse(result);
      return parseGraphData(parsed);
    } catch {
      return null;
    }
  }, [result, analysisType]);

  const handleUpload = async (mode: 'global' | 'local') => {
    const file = fileInputRef.current?.files?.[0];
    if (!file) {
      setError('Выберите файл .json');
      return;
    }
    clearMessages();
    setLoading(true);
    try {
      const text = await file.text();
      const url = `${API_BASE}/arch-graph/api/v1/graph/${
        mode === 'global' ? 'json' : 'local/json'
      }`;
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: text,
      });
      if (!res.ok) {
        const errText = await res.text();
        throw new Error(`Ошибка ${res.status}: ${errText}`);
      }
      const contentType = res.headers.get('content-type') || '';
      if (contentType.includes('application/json')) {
        const data = await res.json();
        setResult(JSON.stringify(data, null, 2));
      } else {
        const plainText = await res.text();
        setResult(plainText);
      }
    } catch (e: any) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteString.trim()) {
      setError('Введите строку для удаления');
      return;
    }
    clearMessages();
    setLoading(true);
    try {
      const query = `MATCH (n {graphTag: "Local ${deleteString}"}) DETACH DELETE n`;
      const res = await fetch(`${API_BASE}/arch-graph/api/v1/elements`, {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json',
          'CYPHER-QUERY': query,
        },
      });
      if (!res.ok) {
        const errText = await res.text();
        throw new Error(`Ошибка ${res.status}: ${errText}`);
      }
      setResult('Локальный граф успешно удалён');
    } catch (e: any) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteGlobal = async () => {
    clearMessages();
    setLoading(true);
    try {
      const query = `MATCH (n {graphTag: "Global"}) DETACH DELETE n`;
      const res = await fetch(`${API_BASE}/arch-graph/api/v1/elements`, {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json',
          'CYPHER-QUERY': query,
        },
      });
      if (!res.ok) {
        const errText = await res.text();
        throw new Error(`Ошибка ${res.status}: ${errText}`);
      }
      setResult('Глобальный граф успешно удалён');
    } catch (e: any) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  const handleAnalyse = async () => {
    clearMessages();
    if (!analysisType) return;
    setLoading(true);
    try {
      const params = new URLSearchParams();
      switch (analysisType) {
        case 'cycles':
        case 'singlepoints':
        case 'godelements':
          if (graphTag) params.append('graphTag', graphTag);
          if (nodeTypes) params.append('nodeTypes', nodeTypes);
          if (relTypes) params.append('relTypes', relTypes);
          if (nodeIdentifiers) params.append('nodeIdentifiers', nodeIdentifiers);
          break;
        case 'pathcapacity':
          if (nodeTypes) params.append('nodeTypes', nodeTypes);
          if (relTypes) params.append('relTypes', relTypes);
          if (nodeIdentifiers) params.append('nodeIdentifiers', nodeIdentifiers);
          break;
        case 'criticalinfrastructure':
          if (nodeIdentifiers) params.append('nodeIdentifiers', nodeIdentifiers);
          break;
        case 'perimeterviolation':
          if (deploymentNodeIdentifier) params.append('deploymentNodeIdentifier', deploymentNodeIdentifier);
          if (properties) params.append('properties', properties);
          break;
      }

      const url = `${API_BASE}/arch-graph/api/v1/analyse/${analysisType}?${params.toString()}`;
      const res = await fetch(url);
      if (!res.ok) {
        const errText = await res.text();
        throw new Error(`Ошибка ${res.status}: ${errText}`);
      }
      const data = await res.json();
      setResult(JSON.stringify(data, null, 2));
    } catch (e: any) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  const getNodeLabel = (node: any) => {
    const identifier = node.structurizr_dsl_identifier ? ` (${node.structurizr_dsl_identifier})` : '';
    return `${node.type}: ${node.label}${identifier}`;
  };

  const renderResult = () => {
    if (loading) return <div className="mt-4 text-center text-gray-600">Выполнение запроса...</div>;
    if (error) return (
      <div className="mt-4 p-3 bg-red-50 border border-red-200 rounded text-red-700 whitespace-pre-wrap">
        <strong>Ошибка:</strong> {error}
      </div>
    );
    if (!result) return null;
  
    // Специальный отчёт для циклических зависимостей
    if (analysisType === 'cycles') {
      try {
        const data = JSON.parse(result);
        const cycles = data.cycles;
        if (!cycles || cycles.length === 0) {
          return (
            <div className="mt-4 p-4 bg-green-50 border border-green-200 rounded text-green-800">
              <p className="font-medium">Циклические зависимости не обнаружены.</p>
              <p className="text-sm mt-1">Архитектура не содержит циклов на анализируемых вершинах и связях.</p>
            </div>
          );
        }
  
        return (
          <div className="mt-4">
            <div className="mb-4 p-4 bg-yellow-50 border border-yellow-200 rounded flex justify-between items-start">
              <div>
                <h3 className="font-semibold text-lg mb-1">Обнаружены циклические зависимости</h3>
                <p className="text-sm text-gray-700">
                  Циклические зависимости между элементами архитектуры могут приводить к проблемам:
                  усложнению тестирования, невозможности независимого развёртывания, 
                  снижению гибкости при замене компонентов. Рекомендуется разорвать циклы, 
                  введя дополнительные абстракции или инвертировав зависимости.
                </p>
              </div>
              <button
                onClick={() => downloadJSON(data, 'cycles_report.json')}
                className="ml-4 text-sm bg-white px-3 py-1 rounded border hover:bg-gray-100 whitespace-nowrap"
              >
                ⬇ Скачать JSON
              </button>
            </div>
  
            {cycles.map((cycle: any, idx: number) => {
              const path = cycle.path;
              if (!path || path.length < 2) return null;

              return (
                <div key={idx} className="mb-4 p-3 bg-white border border-gray-200 rounded shadow-sm">
                  <h4 className="font-medium mb-2">Цикл #{idx + 1}</h4>
                  <div className="flex flex-wrap items-center gap-x-1 gap-y-1 text-sm bg-gray-50 p-2 rounded">
                    {path.map((node: any, nodeIdx: number) => {
                      const name = node.properties?.name || node.name || '?';
                      const type = node.label || node.type || '?';
                      const color = nodeColorMap[type] || defaultColor;
                      return (
                        <React.Fragment key={nodeIdx}>
                          {nodeIdx > 0 && <span className="text-gray-500 mx-1">→</span>}
                          <span className="font-medium">{name}</span>
                          <span
                            className="inline-block px-2 py-0.5 rounded text-xs font-medium text-white"
                            style={{ backgroundColor: color }}
                          >
                            {type}
                          </span>
                        </React.Fragment>
                      );
                    })}
                  </div>
                  {cycle.relationships && (
                    <details className="mt-2">
                      <summary className="cursor-pointer text-sm text-blue-600">Задействованные связи</summary>
                      <ul className="mt-1 text-sm list-disc list-inside">
                        {cycle.relationships.map((rel: any, relIdx: number) => (
                          <li key={relIdx}>
                            {rel.fromName || rel.from} → {rel.toName || rel.to} ({rel.type || 'RELATED'})
                          </li>
                        ))}
                      </ul>
                    </details>
                  )}
                </div>
              );
            })}
          </div>
        );
      } catch {
        return (
          <div className="mt-4 p-3 bg-red-50 border border-red-200 rounded text-red-700">
            Ошибка при обработке результата анализа.
          </div>
        );
      }
    }

    // Специальный отчёт для единых точек отказа
    if (analysisType === 'singlepoints') {
      try {
        const data = JSON.parse(result);
        // Поддержка двух возможных форматов: { nodes: [...] } или { singlePoints: [...] }
        const points = data.singlePoints || data.nodes || [];
        if (!points || points.length === 0) {
          return (
            <div className="mt-4 p-4 bg-green-50 border border-green-200 rounded text-green-800">
              <p className="font-medium">Единые точки отказа не обнаружены.</p>
              <p className="text-sm mt-1">
                В анализируемой архитектуре нет узлов, удаление которых разъединило бы граф на несвязные части.
              </p>
            </div>
          );
        }

        return (
          <div className="mt-4">
            <div className="mb-4 p-4 bg-yellow-50 border border-yellow-200 rounded flex justify-between items-start">
              <div>
                <h3 className="font-semibold text-lg mb-1">Обнаружены единые точки отказа</h3>
                <p className="text-sm text-gray-700">
                  Единая точка отказа (Single Point of Failure) – это узел, выход из строя которого
                  приводит к нарушению связности всей системы или её критической части. Такие узлы
                  снижают отказоустойчивость и должны быть устранены путём введения дублирования
                  или перестроения архитектуры.
                </p>
              </div>
              <button
                onClick={() => downloadJSON(data, 'singlepoints_report.json')}
                className="ml-4 text-sm bg-white px-3 py-1 rounded border hover:bg-gray-100 whitespace-nowrap"
              >
                ⬇ Скачать JSON
              </button>
            </div>

            <div className="overflow-x-auto">
              <table className="min-w-full bg-white border border-gray-200 rounded">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-4 py-2 text-left text-sm font-medium text-gray-700">#</th>
                    <th className="px-4 py-2 text-left text-sm font-medium text-gray-700">Имя</th>
                    <th className="px-4 py-2 text-left text-sm font-medium text-gray-700">Тип</th>
                    <th className="px-4 py-2 text-left text-sm font-medium text-gray-700">Принадлежность</th>
                    <th className="px-4 py-2 text-left text-sm font-medium text-gray-700">Идентификатор</th>
                  </tr>
                </thead>
                <tbody>
                  {points.map((node: any, idx: number) => {
                    const name = node.properties?.name || node.name || '?';
                    const type = node.label || node.type || '?';
                    const graphTag = node.properties?.graphTag || node.graphTag || 'не указана';
                    const dslId = node.properties?.structurizr_dsl_identifier || '';
                    return (
                      <tr key={node.id || idx} className="border-t border-gray-200 hover:bg-gray-50">
                        <td className="px-4 py-2 text-sm">{idx + 1}</td>
                        <td className="px-4 py-2 text-sm font-medium">{name}</td>
                        <td className="px-4 py-2 text-sm">
                          <span
                            className="inline-block px-2 py-0.5 rounded text-xs font-medium"
                            style={{ backgroundColor: nodeColorMap[type] || defaultColor, color: '#fff' }}
                          >
                            {type}
                          </span>
                        </td>
                        <td className="px-4 py-2 text-sm">{graphTag}</td>
                        <td className="px-4 py-2 text-sm text-gray-500">{dslId || '—'}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            <div className="mt-4 p-3 bg-blue-50 border border-blue-200 rounded text-sm text-gray-700">
              <strong>Рекомендация:</strong> Для каждой обнаруженной точки отказа рассмотрите
              возможность дублирования компонента, введения балансировщика нагрузки или
              реорганизации зависимостей так, чтобы не существовало единственного узла,
              соединяющего разные части системы.
            </div>
          </div>
        );
      } catch {
        return (
          <div className="mt-4 p-3 bg-red-50 border border-red-200 rounded text-red-700">
            Ошибка при обработке результата анализа.
          </div>
        );
      }
    }

    // Специальный отчёт для "божественных объектов"
    if (analysisType === 'godelements') {
      try {
        const data = JSON.parse(result);
        const nodes = data.nodes || [];
        const averageDegree = data.averageDegree || 0;

        if (!nodes || nodes.length === 0) {
          return (
            <div className="mt-4 p-4 bg-green-50 border border-green-200 rounded text-green-800">
              <p className="font-medium">Божественные объекты не обнаружены.</p>
              <p className="text-sm mt-1">
                В архитектуре нет узлов с аномально высокой степенью связности.
              </p>
            </div>
          );
        }

        return (
          <div className="mt-4">
            <div className="mb-4 p-4 bg-yellow-50 border border-yellow-200 rounded flex justify-between items-start">
              <div>
                <h3 className="font-semibold text-lg mb-1">Обнаружены «божественные объекты»</h3>
                <p className="text-sm text-gray-700">
                  «Божественный объект» (God Object) – это элемент, который имеет чрезмерно много связей
                  с другими частями системы. Такие узлы нарушают принцип единственной ответственности,
                  усложняют сопровождение и тестирование, становятся узким местом при изменениях.
                  Рекомендуется разделить их на более мелкие компоненты с чёткой ответственностью.
                </p>
              </div>
              <button
                onClick={() => downloadJSON(data, 'godelements_report.json')}
                className="ml-4 text-sm bg-white px-3 py-1 rounded border hover:bg-gray-100 whitespace-nowrap"
              >
                ⬇ Скачать JSON
              </button>
            </div>

            {averageDegree > 0 && (
              <p className="text-sm text-gray-600 mb-3">
                Целевое количество связей: <strong>{averageDegree.toFixed(1)}</strong>
              </p>
            )}

            <div className="overflow-x-auto">
              <table className="min-w-full bg-white border border-gray-200 rounded">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-4 py-2 text-left text-sm font-medium text-gray-700">#</th>
                    <th className="px-4 py-2 text-left text-sm font-medium text-gray-700">Имя</th>
                    <th className="px-4 py-2 text-left text-sm font-medium text-gray-700">Тип</th>
                    <th className="px-4 py-2 text-left text-sm font-medium text-gray-700">Принадлежность</th>
                    <th className="px-4 py-2 text-left text-sm font-medium text-gray-700">Кол-во связей</th>
                    {averageDegree > 0 && (
                      <th className="px-4 py-2 text-left text-sm font-medium text-gray-700">Превышение среднего</th>
                    )}
                  </tr>
                </thead>
                <tbody>
                  {nodes.map((node: any, idx: number) => {
                    const name = node.properties?.name || node.name || '?';
                    const type = node.label || node.type || '?';
                    const graphTag = node.properties?.graphTag || node.graphTag || 'не указана';
                    const degree = node.totalDegree || 0;
                    const excess = averageDegree > 0 ? ((degree / averageDegree - 1) * 100).toFixed(0) : null;

                    return (
                      <tr key={node.id || idx} className="border-t border-gray-200 hover:bg-gray-50">
                        <td className="px-4 py-2 text-sm">{idx + 1}</td>
                        <td className="px-4 py-2 text-sm font-medium">{name}</td>
                        <td className="px-4 py-2 text-sm">
                          <span
                            className="inline-block px-2 py-0.5 rounded text-xs font-medium text-white"
                            style={{ backgroundColor: nodeColorMap[type] || defaultColor }}
                          >
                            {type}
                          </span>
                        </td>
                        <td className="px-4 py-2 text-sm">{graphTag}</td>
                        <td className="px-4 py-2 text-sm font-semibold">{degree}</td>
                        {averageDegree > 0 && (
                          <td className="px-4 py-2 text-sm">
                            {excess !== null ? (
                              <span className={degree > averageDegree ? 'text-red-600' : 'text-green-600'}>
                                {excess}%
                              </span>
                            ) : '—'}
                          </td>
                        )}
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            <div className="mt-4 p-3 bg-blue-50 border border-blue-200 rounded text-sm text-gray-700">
              <strong>Рекомендация:</strong> Проведите декомпозицию узлов с высокой степенью связности.
              Выделите подсистемы, примените паттерны «Фасад» или «Посредник» для уменьшения количества
              прямых зависимостей.
            </div>

            <div className="mt-4 p-4 bg-green-50 border border-green-200 rounded text-sm text-gray-700">
              <strong>Плюсы устранения антипаттерна:</strong>
              <ul className="list-disc list-inside mt-2 space-y-1">
                <li>
                  <strong>Улучшение сопровождаемости</strong> — компоненты с меньшим числом зависимостей
                  проще понять, изменить и протестировать изолированно.
                </li>
                <li>
                  <strong>Снижение риска каскадных изменений</strong> — модификация декомпозированного
                  компонента затрагивает меньшее число других частей системы.
                </li>
                <li>
                  <strong>Повышение тестируемости</strong> — компоненты с чёткой единственной
                  ответственностью легче покрываются юнит- и интеграционными тестами.
                </li>
                <li>
                  <strong>Параллельная разработка</strong> — независимые компоненты могут разрабатываться
                  разными командами одновременно без конфликтов.
                </li>
                <li>
                  <strong>Упрощение развёртывания</strong> — небольшие компоненты можно деплоить и
                  масштабировать независимо, снижая время простоя при обновлениях.
                </li>
              </ul>
            </div>
          </div>
        );
      } catch {
        return (
          <div className="mt-4 p-3 bg-red-50 border border-red-200 rounded text-red-700">
            Ошибка при обработке результата анализа.
          </div>
        );
      }
    }

    // Специальный отчёт для пропускной способности сети
    if (analysisType === 'pathcapacity') {
      try {
        const data = JSON.parse(result);
        const pathNodes: any[] = data.nodes || [];
        const totalRps = data.rps;
        const totalErrorRate = data.error_rate;
        const totalLatency = data.latency;

        if (pathNodes.length === 0) {
          return (
            <div className="mt-4 p-4 bg-green-50 border border-green-200 rounded text-green-800">
              <p className="font-medium">Путь не найден или не указаны идентификаторы узлов.</p>
            </div>
          );
        }

        // Ключевые идентификаторы, заданные пользователем
        const keyIds = nodeIdentifiers.split(',').map(id => id.trim()).filter(Boolean);

        // Определяем экстремумы среди узлов пути
        let minRpsNodeId: number | null = null;
        let minRpsValue = Infinity;
        let maxErrorNodeId: number | null = null;
        let maxErrorValue = -Infinity;
        let maxLatencyNodeId: number | null = null;
        let maxLatencyValue = -Infinity;

        // Целевые значения: макс. RPS, мин. error_rate, мин. задержка
        let targetRps = -Infinity;
        let targetErrorRate = Infinity;
        let targetLatency = Infinity;

        pathNodes.forEach((node: any) => {
          const rps = node.properties?.rps;
          const errorRate = node.properties?.error_rate;
          const latency = node.properties?.latency;

          if (rps !== undefined && rps < minRpsValue) {
            minRpsValue = rps;
            minRpsNodeId = node.id;
          }
          if (errorRate !== undefined && errorRate > maxErrorValue) {
            maxErrorValue = errorRate;
            maxErrorNodeId = node.id;
          }
          if (latency !== undefined && latency > maxLatencyValue) {
            maxLatencyValue = latency;
            maxLatencyNodeId = node.id;
          }

          if (rps !== undefined && rps > targetRps) targetRps = rps;
          if (errorRate !== undefined && errorRate < targetErrorRate) targetErrorRate = errorRate;
          if (latency !== undefined && latency < targetLatency) targetLatency = latency;
        });

        return (
          <div className="mt-4">
            {/* Описание проверки */}
            <div className="mb-4 p-4 bg-yellow-50 border border-yellow-200 rounded flex justify-between items-start">
              <div>
                <h3 className="font-semibold text-lg mb-1">Анализ пропускной способности пути</h3>
                <p className="text-sm text-gray-700">
                  Данный анализ показывает путь между заданными компонентами и вычисляет совокупные
                  характеристики производительности: минимальную пропускную способность (RPS),
                  общую вероятность ошибки и суммарную задержку. Это помогает выявить узкие места,
                  которые ограничивают скорость, надёжность или время отклика цепочки взаимодействий.
                </p>
              </div>
              <button
                onClick={() => downloadJSON(data, 'pathcapacity_report.json')}
                className="ml-4 text-sm bg-white px-3 py-1 rounded border hover:bg-gray-100 whitespace-nowrap"
              >
                ⬇ Скачать JSON
              </button>
            </div>

            {/* Сводка итоговых метрик */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-3">
              <div className="p-3 bg-blue-50 border border-blue-200 rounded">
                <div className="text-sm text-gray-500">Минимальный RPS</div>
                <div className="text-xl font-bold">{totalRps}</div>
              </div>
              <div className="p-3 bg-red-50 border border-red-200 rounded">
                <div className="text-sm text-gray-500">Общий error_rate</div>
                <div className="text-xl font-bold">{(100 - totalErrorRate * 100).toFixed(2)}%</div>
              </div>
              <div className="p-3 bg-purple-50 border border-purple-200 rounded">
                <div className="text-sm text-gray-500">Суммарная задержка</div>
                <div className="text-xl font-bold">{totalLatency?.toFixed(2)} мс</div>
              </div>
            </div>

            {/* Целевые значения */}
            <div className="mb-4 p-3 bg-gray-50 border border-gray-200 rounded text-sm text-gray-600">
              <strong>Целевые значения в цепочке:</strong>
              <span className="ml-3">RPS: <strong>{targetRps !== -Infinity ? targetRps : '—'}</strong></span>
              <span className="ml-3">error_rate: <strong>{targetErrorRate !== Infinity ? (targetErrorRate * 100).toFixed(2) + '%' : '—'}</strong></span>
              <span className="ml-3">Задержка: <strong>{targetLatency !== Infinity ? targetLatency.toFixed(2) + ' мс' : '—'}</strong></span>
            </div>

            {/* Цепочка узлов пути */}
            <h4 className="font-medium mb-2">Путь взаимодействия</h4>
            <div className="flex flex-wrap items-center gap-x-1 gap-y-1 text-sm bg-gray-50 p-3 rounded mb-4">
              {pathNodes.map((node: any, nodeIdx: number) => {
                const name = node.properties?.name || node.name || '?';
                const type = node.label || node.type || '?';
                const color = nodeColorMap[type] || defaultColor;
                const dslId = node.properties?.structurizr_dsl_identifier || '';
                const isKey = keyIds.includes(dslId);
                const isMinRps = node.id === minRpsNodeId;
                const isMaxError = node.id === maxErrorNodeId;
                const isMaxLatency = node.id === maxLatencyNodeId;

                const badges: string[] = [];
                if (isMinRps) badges.push('🔻 мин. RPS');
                if (isMaxError) badges.push('⚠️ макс. ошибка');
                if (isMaxLatency) badges.push('🐌 макс. задержка');

                return (
                  <React.Fragment key={nodeIdx}>
                    {nodeIdx > 0 && <span className="text-gray-500 mx-1">→</span>}
                    <span
                      className={`inline-flex items-center gap-1 rounded p-1 ${
                        isKey ? 'bg-yellow-100 ring-2 ring-yellow-400' : ''
                      }`}
                    >
                      <span className="font-medium">{name}</span>
                      <span
                        className="inline-block px-2 py-0.5 rounded text-xs font-medium text-white"
                        style={{ backgroundColor: color }}
                      >
                        {type}
                      </span>
                      {badges.length > 0 && (
                        <span className="text-xs text-gray-600 ml-1">{badges.join(', ')}</span>
                      )}
                    </span>
                  </React.Fragment>
                );
              })}
            </div>

            {/* Рекомендации */}
            <div className="mt-4 p-4 bg-blue-50 border border-blue-200 rounded text-sm text-gray-700">
              <strong>Рекомендации:</strong>
              <ul className="list-disc list-inside mt-2 space-y-1">
                {minRpsNodeId && (
                  <li>
                    Узел с минимальной пропускной способностью (RPS = {minRpsValue}) ограничивает весь путь.
                    Рассмотрите масштабирование или оптимизацию этого компонента.
                  </li>
                )}
                {maxErrorNodeId && (
                  <li>
                    Узел с наибольшей вероятностью ошибки (error_rate = {maxErrorValue})
                    вносит основной вклад в ненадёжность цепочки. Повысьте его отказоустойчивость
                    или добавьте механизмы повторных попыток.
                  </li>
                )}
                {maxLatencyNodeId && (
                  <li>
                    Узел с наибольшей задержкой (latency = {maxLatencyValue} мс) увеличивает общее время отклика.
                    Оптимизируйте его производительность или примените кэширование.
                  </li>
                )}
                {keyIds.length > 0 && (
                  <li>
                    Проверьте выделенные ключевые узлы (жёлтая рамка) на соответствие нефункциональным требованиям.
                  </li>
                )}
                <li>
                  Для общего повышения пропускной способности пути стремитесь сбалансировать RPS и задержки,
                  а также уменьшить вероятности ошибок на каждом шаге.
                </li>
              </ul>
            </div>

            <div className="mt-4 p-4 bg-green-50 border border-green-200 rounded text-sm text-gray-700">
              <strong>Плюсы устранения антипаттерна:</strong>
              <ul className="list-disc list-inside mt-2 space-y-1">
                <li>
                  <strong>Повышение пропускной способности</strong> — устранение узкого места позволяет
                  всей цепочке обрабатывать нагрузку на уровне лучших компонентов, а не худшего.
                </li>
                <li>
                  <strong>Снижение задержек</strong> — оптимизация медленных узлов сокращает суммарное
                  время отклика цепочки взаимодействий.
                </li>
                <li>
                  <strong>Повышение надёжности</strong> — снижение error_rate в проблемных компонентах
                  уменьшает вероятность отказа всей цепочки.
                </li>
                <li>
                  <strong>Предсказуемость поведения под нагрузкой</strong> — сбалансированные характеристики
                  компонентов исключают непредвиденные деградации при пиковых запросах.
                </li>
                <li>
                  <strong>Упрощение масштабирования</strong> — равномерно распределённые узкие места
                  позволяют горизонтально масштабировать всю цепочку без точечных «аварийных» мер.
                </li>
              </ul>
            </div>
          </div>
        );
      } catch {
        return (
          <div className="mt-4 p-3 bg-red-50 border border-red-200 rounded text-red-700">
            Ошибка при обработке результата анализа.
          </div>
        );
      }
    }

    // Специальный отчёт для критической инфраструктуры (общие узлы развёртывания)
    if (analysisType === 'criticalinfrastructure') {
      try {
        const data = JSON.parse(result);
        const nodes = data.nodes || [];
        const systemIds = nodeIdentifiers
          .split(',')
          .map(id => id.trim())
          .filter(Boolean);

        if (!nodes || nodes.length === 0) {
          return (
            <div className="mt-4 p-4 bg-green-50 border border-green-200 rounded text-green-800">
              <p className="font-medium">Cкрытое совместное использование инфраструктуры не обнаружено.</p>
              <p className="text-sm mt-1">
                Системы, перечисленные в идентификаторах, не имеют общих узлов развёртывания.
              </p>
            </div>
          );
        }

        return (
          <div className="mt-4">
            <div className="mb-4 p-4 bg-yellow-50 border border-yellow-200 rounded flex justify-between items-start">
              <div>
                <h3 className="font-semibold text-lg mb-1">
                  Обнаружена критическая инфраструктура (общие узлы развёртывания)
                </h3>
                <p className="text-sm text-gray-700">
                  Обнаружены узлы развёртывания, на которых размещены компоненты нескольких
                  различных систем. Отказ такого узла может одновременно нарушить работу
                  всех зависимых систем, создавая неприемлемый риск для всей инфраструктуры.
                  Это не классическая единая точка отказа на уровне логических зависимостей,
                  а риск совместного размещения, который необходимо контролировать.
                </p>
              </div>
              <button
                onClick={() => downloadJSON(data, 'criticalinfrastructure_report.json')}
                className="ml-4 text-sm bg-white px-3 py-1 rounded border hover:bg-gray-100 whitespace-nowrap"
              >
                ⬇ Скачать JSON
              </button>
            </div>

            {/* Информация о системах */}
            {systemIds.length > 0 && (
              <div className="mb-3 p-2 bg-gray-50 rounded text-sm text-gray-600">
                Анализируются системы: <strong>{systemIds.join(', ')}</strong>
              </div>
            )}

            {/* Таблица найденных узлов */}
            <div className="overflow-x-auto">
              <table className="min-w-full bg-white border border-gray-200 rounded">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-4 py-2 text-left text-sm font-medium text-gray-700">#</th>
                    <th className="px-4 py-2 text-left text-sm font-medium text-gray-700">Имя</th>
                    <th className="px-4 py-2 text-left text-sm font-medium text-gray-700">Тип</th>
                    <th className="px-4 py-2 text-left text-sm font-medium text-gray-700">Принадлежность</th>
                    <th className="px-4 py-2 text-left text-sm font-medium text-gray-700">Идентификатор</th>
                  </tr>
                </thead>
                <tbody>
                  {nodes.map((node: any, idx: number) => {
                    const name = node.properties?.name || node.name || '?';
                    const type = node.label || node.type || '?';
                    const graphTag = node.properties?.graphTag || node.graphTag || 'не указана';
                    const dslId = node.properties?.structurizr_dsl_identifier || '';
                    return (
                      <tr key={node.id || idx} className="border-t border-gray-200 hover:bg-gray-50">
                        <td className="px-4 py-2 text-sm">{idx + 1}</td>
                        <td className="px-4 py-2 text-sm font-medium">{name}</td>
                        <td className="px-4 py-2 text-sm">
                          <span
                            className="inline-block px-2 py-0.5 rounded text-xs font-medium text-white"
                            style={{
                              backgroundColor: nodeColorMap[type] || defaultColor,
                            }}
                          >
                            {type}
                          </span>
                        </td>
                        <td className="px-4 py-2 text-sm">{graphTag}</td>
                        <td className="px-4 py-2 text-sm text-gray-500">{dslId || '—'}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {/* Рекомендации */}
            <div className="mt-4 p-3 bg-blue-50 border border-blue-200 rounded text-sm text-gray-700">
              <strong>Рекомендации:</strong>
              <ul className="list-disc list-inside mt-2 space-y-1">
                <li>
                  Рассмотрите возможность размещения компонентов разных систем на
                  независимых физических или виртуальных узлах, чтобы снизить эффект
                  отказа общего инфраструктурного элемента.
                </li>
                <li>
                  Если совместное размещение неизбежно, обеспечьте повышенную
                  отказоустойчивость критического узла (кластеризация, резервирование,
                  автоматическое переключение).
                </li>
                <li>
                  Проведите стресс-тестирование и анализ влияния отказа каждого из
                  перечисленных узлов на все зависимые системы.
                </li>
                <li>
                  Задокументируйте данные узлы как критические точки совместного
                  размещения и включите их в план аварийного восстановления.
                </li>
              </ul>
            </div>
          </div>
        );
      } catch {
        return (
          <div className="mt-4 p-3 bg-red-50 border border-red-200 rounded text-red-700">
            Ошибка при обработке результата анализа.
          </div>
        );
      }
    }

    // Специальный отчёт для контроля периметра (нарушение периметра развёртывания)
    if (analysisType === 'perimeterviolation') {
      try {
        const data = JSON.parse(result);
        const nodes = data.nodes || [];

        // Свойства, которые должны присутствовать (заданы пользователем)
        const requiredProps = properties
          .split(',')
          .map(p => p.trim())
          .filter(Boolean);

        if (!nodes || nodes.length === 0) {
          return (
            <div className="mt-4 p-4 bg-green-50 border border-green-200 rounded text-green-800">
              <p className="font-medium">Нарушений периметра не обнаружено.</p>
              <p className="text-sm mt-1">
                Все компоненты, развёрнутые в указанной зоне {deploymentNodeIdentifier || ''},
                соответствуют заданным технологическим требованиям.
              </p>
            </div>
          );
        }

        return (
          <div className="mt-4">
            {/* Описание антипаттерна */}
            <div className="mb-4 p-4 bg-yellow-50 border border-yellow-200 rounded flex justify-between items-start">
              <div>
                <h3 className="font-semibold text-lg mb-1">
                  Обнаружено нарушение сетевого периметра
                </h3>
                <p className="text-sm text-gray-700">
                  В сетевой зоне <strong>{deploymentNodeIdentifier || 'указанный узел'}</strong> найдены
                  компоненты, которые не обладают необходимыми технологиями (например, WAF).
                  Это означает, что трафик может проходить в обход защитных средств, создавая угрозу
                  безопасности. Все компоненты, развёрнутые в DMZ или иных контролируемых зонах,
                  должны взаимодействовать с внешними сетями только через заданные технологии защиты.
                </p>
              </div>
              <button
                onClick={() => downloadJSON(data, 'perimeterviolation_report.json')}
                className="ml-4 text-sm bg-white px-3 py-1 rounded border hover:bg-gray-100 whitespace-nowrap"
              >
                ⬇ Скачать JSON
              </button>
            </div>

            {/* Таблица найденных нарушителей */}
            <div className="overflow-x-auto">
              <table className="min-w-full bg-white border border-gray-200 rounded">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-4 py-2 text-left text-sm font-medium text-gray-700">#</th>
                    <th className="px-4 py-2 text-left text-sm font-medium text-gray-700">Имя</th>
                    <th className="px-4 py-2 text-left text-sm font-medium text-gray-700">Тип</th>
                    <th className="px-4 py-2 text-left text-sm font-medium text-gray-700">Принадлежность</th>
                    <th className="px-4 py-2 text-left text-sm font-medium text-gray-700">Текущая технология</th>
                    <th className="px-4 py-2 text-left text-sm font-medium text-gray-700">Требуемые технологии</th>
                  </tr>
                </thead>
                <tbody>
                  {nodes.map((node: any, idx: number) => {
                    const name = node.properties?.name || node.name || '?';
                    const type = node.label || node.type || '?';
                    const graphTag = node.properties?.graphTag || node.graphTag || 'не указана';
                    const currentTech = node.properties?.technology || 'не указана';
                    const dslId = node.properties?.structurizr_dsl_identifier || '';

                    return (
                      <tr key={node.id || idx} className="border-t border-gray-200 hover:bg-gray-50">
                        <td className="px-4 py-2 text-sm">{idx + 1}</td>
                        <td className="px-4 py-2 text-sm font-medium">{name}</td>
                        <td className="px-4 py-2 text-sm">
                          <span
                            className="inline-block px-2 py-0.5 rounded text-xs font-medium text-white"
                            style={{
                              backgroundColor: nodeColorMap[type] || defaultColor,
                            }}
                          >
                            {type}
                          </span>
                        </td>
                        <td className="px-4 py-2 text-sm">{graphTag}</td>
                        <td className="px-4 py-2 text-sm">
                          {currentTech && currentTech !== 'не указана' ? (
                            <span className="text-orange-600 font-medium">{currentTech}</span>
                          ) : (
                            <span className="text-red-600 italic">отсутствует</span>
                          )}
                        </td>
                        <td className="px-4 py-2 text-sm text-gray-500">
                          {requiredProps.length > 0 ? requiredProps.join(', ') : 'не заданы'}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {/* Рекомендации */}
            <div className="mt-4 p-3 bg-blue-50 border border-blue-200 rounded text-sm text-gray-700">
              <strong>Рекомендации:</strong>
              <ul className="list-disc list-inside mt-2 space-y-1">
                <li>
                  Убедитесь, что все компоненты, развёрнутые в зоне{' '}
                  <strong>{deploymentNodeIdentifier || 'указанной зоне'}</strong>, явно
                  указывают требуемую технологию защиты (например, WAF).
                </li>
                <li>
                  Проверьте, что сетевые взаимодействия проходят через контейнеры или
                  экземпляры, реализующие необходимый стек безопасности.
                </li>
                <li>
                  Для компонентов, у которых технология отсутствует, либо добавьте
                  соответствующий атрибут, либо перенаправьте их трафик через
                  доверенные узлы с нужной технологией.
                </li>
                <li>
                  Регулярно проводите аудит периметра развёртывания на предмет
                  появления новых непомеченных компонентов.
                </li>
              </ul>
            </div>
          </div>
        );
      } catch {
        return (
          <div className="mt-4 p-3 bg-red-50 border border-red-200 rounded text-red-700">
            Ошибка при обработке результата анализа.
          </div>
        );
      }
    }

    return (
      <div className="mt-4">
        <div className="flex items-center justify-between mb-2">
          <h3 className="font-medium">Результат:</h3>
          {analysisGraphData && (
            <button
              onClick={() => setViewMode(viewMode === 'json' ? 'graph' : 'json')}
              className="text-sm bg-gray-200 px-3 py-1 rounded hover:bg-gray-300"
            >
              {viewMode === 'json' ? 'Показать граф' : 'Показать JSON'}
            </button>
          )}
        </div>

        {viewMode === 'json' || !analysisGraphData ? (
          <pre className="whitespace-pre-wrap text-sm p-3 bg-green-50 border border-green-200 rounded text-gray-800">
            {result}
          </pre>
        ) : (
          <div className="border rounded overflow-hidden bg-white" style={{ height: '600px' }}>
            <ForceGraph2D
              graphData={analysisGraphData}
              nodeLabel={getNodeLabel}
              nodeColor={(node: any) => nodeColorMap[node.type] || defaultColor}
              linkLabel={(link: any) => link.label}
              nodeRelSize={6}
              linkDirectionalArrowLength={3.5}
              linkDirectionalArrowRelPos={1}
              width={800}
              height={600}
              enableNodeDrag={true}
              enableZoomInteraction={true}
            />
          </div>
        )}
      </div>
    );
  };

  const getAnalysisTitle = () => {
    switch (analysisType) {
      case 'cycles': return 'Поиск циклических зависимостей';
      case 'singlepoints': return 'Поиск единых точек отказа';
      case 'godelements': return 'Поиск "божественных объектов"';
      case 'pathcapacity': return 'Поиск бутылочных горлышек';
      case 'criticalinfrastructure': return 'Поиск скрытого совместного использования инфраструктуры';
      case 'perimeterviolation': return 'Поиск нарушения сетевого периметра';
      default: return '';
    }
  };

  return (
    <main className="max-w-4xl mx-auto p-6">
      <h1 className="text-2xl font-bold mb-4 text-center">
        Выпускная квалификационная работа на тему «Метод анализа архитектуры программного обеспечения на поиск уязвимостей»
      </h1>

      <div className="flex space-x-4 mb-6 border-b pb-2 relative">
        <button
          onClick={() => { setActiveTab('visualize'); setAnalysisType(null); }}
          className={`px-4 py-2 font-medium ${activeTab === 'visualize' ? 'border-b-2 border-blue-600 text-blue-600' : 'text-gray-600'}`}
        >
          Визуализация графа
        </button>
        <button
          onClick={() => { setActiveTab('upload'); setAnalysisType(null); clearMessages(); }}
          className={`px-4 py-2 font-medium ${activeTab === 'upload' ? 'border-b-2 border-blue-600 text-blue-600' : 'text-gray-600'}`}
        >
          Загрузка системы в граф
        </button>
        <button
          onClick={() => { setActiveTab('delete'); setAnalysisType(null); clearMessages(); }}
          className={`px-4 py-2 font-medium ${activeTab === 'delete' ? 'border-b-2 border-blue-600 text-blue-600' : 'text-gray-600'}`}
        >
          Удаление графа
        </button>
        <div className="relative">
          <button
            onClick={() => setAnalysisOpen(!analysisOpen)}
            onBlur={() => setTimeout(() => setAnalysisOpen(false), 100)}
            className={`px-4 py-2 font-medium flex items-center ${activeTab === 'analysis' ? 'border-b-2 border-blue-600 text-blue-600' : 'text-gray-600'}`}
          >
            Анализ
            <svg className="w-4 h-4 ml-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 9l-7 7-7-7" />
            </svg>
          </button>
          {analysisOpen && (
            <div className="absolute left-0 mt-1 w-80 bg-white border rounded shadow-lg z-10">
              {[
                { type: 'cycles', label: 'Поиск циклических зависимостей' },
                { type: 'singlepoints', label: 'Поиск единых точек отказа' },
                { type: 'godelements', label: 'Поиск "божественных объектов"' },
                { type: 'pathcapacity', label: 'Поиск бутылочных горлышек' },
                { type: 'criticalinfrastructure', label: 'Поиск скрытого совместного использования инфраструктуры' },
                { type: 'perimeterviolation', label: 'Поиск нарушения сетевого периметра' },
              ].map(item => (
                <button
                  key={item.type}
                  className="block w-full text-left px-4 py-2 hover:bg-gray-100"
                  onClick={() => {
                    setActiveTab('analysis');
                    setAnalysisType(item.type as typeof analysisType);
                    setAnalysisOpen(false);
                    clearMessages();
                  }}
                >
                  {item.label}
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      <div className="bg-white p-6 rounded shadow">
        {/* Визуализация */}
        {activeTab === 'visualize' && (
          <div>
            {visLoading && <div className="text-center text-gray-600 py-8">Загрузка графа...</div>}
            {visError && (
              <div className="p-3 bg-red-50 border border-red-200 rounded text-red-700">
                <strong>Ошибка загрузки графа:</strong> {visError}
              </div>
            )}
            {!visLoading && !visError && visResult && (
              <div className="mt-4">
                <div className="flex items-center justify-between mb-2">
                  <h3 className="font-medium">Результат:</h3>
                  <button
                    onClick={() => setVisViewMode(visViewMode === 'json' ? 'graph' : 'json')}
                    className="text-sm bg-gray-200 px-3 py-1 rounded hover:bg-gray-300"
                  >
                    {visViewMode === 'json' ? 'Показать граф' : 'Показать JSON'}
                  </button>
                </div>
                {visViewMode === 'json' ? (
                  <pre className="whitespace-pre-wrap text-sm p-3 bg-green-50 border border-green-200 rounded text-gray-800">
                    {JSON.stringify(visResult, null, 2)}
                  </pre>
                ) : (
                  visGraphData && visGraphData.nodes?.length > 0 ? (
                    <div className="border rounded overflow-hidden bg-white" style={{ height: '600px' }}>
                      <ForceGraph2D
                        graphData={visGraphData}
                        nodeLabel={getNodeLabel}
                        nodeColor={(node: any) => nodeColorMap[node.type] || defaultColor}
                        linkLabel={(link: any) => link.label}
                        nodeRelSize={6}
                        linkDirectionalArrowLength={3.5}
                        linkDirectionalArrowRelPos={1}
                        width={800}
                        height={600}
                        enableNodeDrag={true}
                        enableZoomInteraction={true}
                      />
                    </div>
                  ) : (
                    <div className="text-center text-gray-500 py-8">Граф пуст или данные не получены.</div>
                  )
                )}
              </div>
            )}
          </div>
        )}

        {/* Загрузка */}
        {activeTab === 'upload' && (
          <div>
            <h2 className="text-lg font-semibold mb-3">Загрузка системы в граф</h2>
            <div className="mb-4">
              <label className="block text-sm font-medium mb-1">Загрузить файл .json с системой</label>
              <input
                ref={fileInputRef}
                type="file"
                accept=".json"
                className="block w-full text-sm border border-gray-300 rounded p-2"
              />
            </div>
            <div className="flex space-x-4">
              <button onClick={() => handleUpload('global')} disabled={loading}
                className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700 disabled:opacity-50">
                Загрузить систему в глобальный граф
              </button>
              <button onClick={() => handleUpload('local')} disabled={loading}
                className="bg-green-600 text-white px-4 py-2 rounded hover:bg-green-700 disabled:opacity-50">
                Загрузить систему в локальный граф
              </button>
            </div>
          </div>
        )}

        {/* Удаление */}
        {activeTab === 'delete' && (
          <div>
            {/* Блок удаления локального графа */}
            <div className="mb-6">
              <h2 className="text-lg font-semibold mb-3">Удаление локального графа</h2>
              <div className="mb-3">
                <label className="block text-sm font-medium mb-1">Удалить локальный граф</label>
                <input
                  type="text"
                  value={deleteString}
                  onChange={(e) => setDeleteString(e.target.value)}
                  placeholder="Введите строку идентификатора"
                  className="w-full border border-gray-300 rounded p-2"
                />
                <p className="text-xs text-gray-500 mt-1">
                  Будет выполнен запрос: MATCH (n {'{'}graphTag: &quot;Local СТРОКА&quot;{'}'}) DETACH DELETE n
                </p>
              </div>
              <button onClick={handleDelete} disabled={loading}
                className="bg-red-600 text-white px-4 py-2 rounded hover:bg-red-700 disabled:opacity-50">
                Удалить локальный граф
              </button>
            </div>

            {/* Блок удаления глобального графа */}
            <div>
              <h2 className="text-lg font-semibold mb-3">Удаление глобального графа</h2>
              <p className="text-xs text-gray-500 mb-3">
                Будет выполнен запрос: MATCH (n {'{'}graphTag: &quot;Global&quot;{'}'}) DETACH DELETE n
              </p>
              <button onClick={handleDeleteGlobal} disabled={loading}
                className="bg-red-800 text-white px-4 py-2 rounded hover:bg-red-900 disabled:opacity-50">
                Удалить глобальный граф
              </button>
            </div>
          </div>
        )}

        {/* Анализ */}
        {activeTab === 'analysis' && analysisType && (
          <div>
            <h2 className="text-lg font-semibold mb-3">{getAnalysisTitle()}</h2>

            {/* Общие поля для cycles, singlepoints, godelements */}
            {(analysisType === 'cycles' || analysisType === 'singlepoints' || analysisType === 'godelements') && (
              <div>
                <p className="text-sm text-gray-500 mb-3">При пустом поле фильтрация по данному критерию не производится</p>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-4">
                  <div>
                    <label className="block text-sm font-medium mb-1">Вид графа (graphTag)</label>
                    <input type="text" value={graphTag} onChange={(e) => setGraphTag(e.target.value)} placeholder="global" className="w-full border border-gray-300 rounded p-2" />
                  </div>
                  <div>
                    <label className="block text-sm font-medium mb-1">Типы вершин (nodeTypes)</label>
                    <input type="text" value={nodeTypes} onChange={(e) => setNodeTypes(e.target.value)} placeholder="Container,Component" className="w-full border border-gray-300 rounded p-2" />
                  </div>
                  <div>
                    <label className="block text-sm font-medium mb-1">Типы связей (relTypes)</label>
                    <input type="text" value={relTypes} onChange={(e) => setRelTypes(e.target.value)} placeholder="CALLS,USES" className="w-full border border-gray-300 rounded p-2" />
                  </div>
                  <div>
                    <label className="block text-sm font-medium mb-1">Идентификаторы вершин (nodeIdentifiers)</label>
                    <input type="text" value={nodeIdentifiers} onChange={(e) => setNodeIdentifiers(e.target.value)} placeholder="id1,id2" className="w-full border border-gray-300 rounded p-2" />
                  </div>
                </div>
              </div>
            )}

            {/* pathCapacity */}
            {analysisType === 'pathcapacity' && (
              <div>
                <p className="text-sm text-gray-500 mb-3">При пустом поле фильтрация по данному критерию не производится</p>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-4">
                  <div>
                    <label className="block text-sm font-medium mb-1">Типы вершин (nodeTypes)</label>
                    <input type="text" value={nodeTypes} onChange={(e) => setNodeTypes(e.target.value)} placeholder="Container,Component" className="w-full border border-gray-300 rounded p-2" />
                  </div>
                  <div>
                    <label className="block text-sm font-medium mb-1">Типы связей (relTypes)</label>
                    <input type="text" value={relTypes} onChange={(e) => setRelTypes(e.target.value)} placeholder="CALLS,USES" className="w-full border border-gray-300 rounded p-2" />
                  </div>
                  <div>
                    <label className="block text-sm font-medium mb-1">Идентификаторы вершин (nodeIdentifiers)</label>
                    <input type="text" value={nodeIdentifiers} onChange={(e) => setNodeIdentifiers(e.target.value)} placeholder="id1,id2" className="w-full border border-gray-300 rounded p-2" />
                  </div>
                </div>
              </div>
            )}

            {/* criticalInfrastructure */}
            {analysisType === 'criticalinfrastructure' && (
              <div>
                <p className="text-sm text-gray-500 mb-3">При пустом поле фильтрация не производится</p>
                <div className="mb-4">
                  <label className="block text-sm font-medium mb-1">Идентификаторы систем (nodeIdentifiers)</label>
                  <input type="text" value={nodeIdentifiers} onChange={(e) => setNodeIdentifiers(e.target.value)} placeholder="id1,id2" className="w-full border border-gray-300 rounded p-2" />
                </div>
              </div>
            )}

            {/* perimeterViolation */}
            {analysisType === 'perimeterviolation' && (
              <div>
                <p className="text-sm text-gray-500 mb-3">При пустом поле фильтрация не производится</p>
                <div className="flex flex-col sm:flex-row sm:space-x-4 space-y-4 sm:space-y-0 mb-4">
                  <div className="sm:w-1/2">
                    <div className="h-12 flex items-end mb-1">
                      <label className="text-sm font-medium">
                        Идентификатор узла развёртывания (deploymentNodeIdentifier)
                      </label>
                    </div>
                    <input
                      type="text"
                      value={deploymentNodeIdentifier}
                      onChange={(e) => setDeploymentNodeIdentifier(e.target.value)}
                      placeholder="dn1"
                      className="w-full border border-gray-300 rounded p-2"
                    />
                  </div>
                  <div className="sm:w-1/2">
                    <div className="h-12 flex items-end mb-1">
                      <label className="text-sm font-medium">
                        Необходимые параметры (properties)
                      </label>
                    </div>
                    <input
                      type="text"
                      value={properties}
                      onChange={(e) => setProperties(e.target.value)}
                      placeholder="key=value"
                      className="w-full border border-gray-300 rounded p-2"
                    />
                  </div>
                </div>
              </div>
            )}

            <button onClick={handleAnalyse} disabled={loading}
              className="bg-indigo-600 text-white px-4 py-2 rounded hover:bg-indigo-700 disabled:opacity-50">
              Проанализировать
            </button>
          </div>
        )}

        {activeTab !== 'visualize' && renderResult()}
      </div>
    </main>
  );
}