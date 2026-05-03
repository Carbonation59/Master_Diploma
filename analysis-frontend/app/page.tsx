'use client';

import { useState, useRef, useMemo, useEffect, useCallback } from 'react';
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
  SoftwareSystem: '#ffff00',
  DeploymentNode: '#964b00',
  InfrastructureNode: '#42aaff',
  ContainerInstance: '#ffc0cb',
};
const defaultColor = '#9467bd';

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

  // Доп. метрики для pathCapacity
  const [pathMetrics, setPathMetrics] = useState<{ error_rate?: number; throughput?: number } | null>(null);

  const clearMessages = () => {
    setResult(null);
    setError(null);
    setViewMode('json');
    setPathMetrics(null);
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
      if (analysisType === 'pathcapacity') {
        setPathMetrics({
          error_rate: parsed.error_rate,
          throughput: parsed.throughput,
        });
      } else {
        setPathMetrics(null);
      }
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

        {/* Метрики для pathCapacity */}
        {analysisType === 'pathcapacity' && pathMetrics && (
          <div className="mb-3 p-3 bg-blue-50 border border-blue-200 rounded text-sm">
            {pathMetrics.error_rate !== undefined && (
              <div><strong>Error rate:</strong> {pathMetrics.error_rate}</div>
            )}
            {pathMetrics.throughput !== undefined && (
              <div><strong>Throughput:</strong> {pathMetrics.throughput}</div>
            )}
          </div>
        )}

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
      case 'cycles': return 'Поиск циклических зависимостей в архитектуре';
      case 'singlepoints': return 'Поиск единых точек отказа в архитектуре';
      case 'godelements': return 'Поиск "божественного объекта" в архитектуре';
      case 'pathcapacity': return 'Пропускная способность сети';
      case 'criticalinfrastructure': return 'Поиск критической инфраструктуры (общих узлов развёртывания)';
      case 'perimeterviolation': return 'Контроль периметра (нарушение периметра развёртывания)';
      default: return '';
    }
  };

  return (
    <main className="max-w-4xl mx-auto p-6">
      <h1 className="text-2xl font-bold mb-4 text-center">
        Выпускная квалификационная работа магистра на тему «Метод анализа архитектуры программного обеспечения на поиск уязвимостей»
      </h1>

      <div className="flex space-x-4 mb-6 border-b pb-2 relative">
        <button
          onClick={() => setActiveTab('visualize')}
          className={`px-4 py-2 font-medium ${activeTab === 'visualize' ? 'border-b-2 border-blue-600 text-blue-600' : 'text-gray-600'}`}
        >
          Визуализация графа
        </button>
        <button
          onClick={() => { setActiveTab('upload'); clearMessages(); }}
          className={`px-4 py-2 font-medium ${activeTab === 'upload' ? 'border-b-2 border-blue-600 text-blue-600' : 'text-gray-600'}`}
        >
          Загрузка системы в граф
        </button>
        <button
          onClick={() => { setActiveTab('delete'); clearMessages(); }}
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
                { type: 'cycles', label: 'Поиск циклических зависимостей в архитектуре' },
                { type: 'singlepoints', label: 'Поиск единых точек отказа в архитектуре' },
                { type: 'godelements', label: 'Поиск "божественного объекта" в архитектуре' },
                { type: 'pathcapacity', label: 'Пропускная способность сети' },
                { type: 'criticalinfrastructure', label: 'Поиск критической инфраструктуры (общих узлов развёртывания)' },
                { type: 'perimeterviolation', label: 'Контроль периметра (нарушение периметра развёртывания)' },
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