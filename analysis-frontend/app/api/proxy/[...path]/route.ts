import { NextRequest, NextResponse } from 'next/server';

const TARGET_BASE = process.env.API_URL || 'http://gateway:8080';

export async function GET(request: NextRequest) {
  return proxyRequest(request, 'GET');
}

export async function POST(request: NextRequest) {
  return proxyRequest(request, 'POST');
}

export async function DELETE(request: NextRequest) {
  return proxyRequest(request, 'DELETE');
}

async function proxyRequest(request: NextRequest, method: string) {
  // Извлекаем путь после /api/proxy/
  const url = new URL(request.url);
  let path = url.pathname.replace(/^\/api\/proxy/, '');
  // Добавляем query-параметры
  const targetUrl = `${TARGET_BASE}${path}${url.search}`;

  try {
    const headers = new Headers();
    // Прокидываем нужные заголовки
    const cypherHeader = request.headers.get('cypher-query');
    if (cypherHeader) {
      headers.set('CYPHER-QUERY', cypherHeader);
    }
    // Если нужно прокинуть Content-Type, сделайте это
    // headers.set('Content-Type', request.headers.get('content-type') || 'application/json');

    const body = method === 'GET' || method === 'DELETE' ? undefined : await request.text();

    const response = await fetch(targetUrl, {
      method,
      headers,
      body,
    });

    const data = await response.text();
    return new NextResponse(data, {
      status: response.status,
      statusText: response.statusText,
      headers: {
        'Content-Type': response.headers.get('content-type') || 'application/json',
      },
    });
  } catch (error: any) {
    return new NextResponse(JSON.stringify({ error: error.message }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' },
    });
  }
}